package com.example.tejpphone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.util.Collections;

/**
 * A live camera picture behind the fence, so a real fence can be traced. On a Meta Quest 3 or 3S
 * this is a passthrough camera, elsewhere the back camera.
 *
 * <p>The picture fills the view and is cropped to it. The camera is only open between
 * {@link #start()} and {@link #stop()}; the caller must have camera permission before starting.
 */
final class CameraBackground implements TextureView.SurfaceTextureListener {

    interface Listener {
        void onCameraFailed(String message);
    }

    private static final String TAG = "CameraBackground";
    /** Quest vendor tag, from Meta's Passthrough Camera API samples. Source 0 is a passthrough camera. */
    private static final String KEY_CAMERA_SOURCE = "com.meta.extra_metadata.camera_source";
    private static final int CAMERA_SOURCE_PASSTHROUGH = 0;
    /** Larger previews cost memory and battery without looking any better behind the grid. */
    private static final int MAX_PREVIEW_SIDE = 1920;

    /** Opening and closing the camera is slow, so it happens on a thread of its own. */
    private static Handler cameraHandler;

    private final Context context;
    private final TextureView view;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started = false;   // main thread

    // Camera thread only
    private boolean wanted = false;
    private boolean opening = false;
    private SurfaceTexture texture;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private boolean continuousFocus;

    // Written on the camera thread, read on the main thread for the view transform
    private volatile Size previewSize;
    private volatile int sensorOrientation;

    CameraBackground(Context context, TextureView view, Listener listener) {
        this.context = context.getApplicationContext();
        this.view = view;
        this.listener = listener;
        if (cameraHandler == null) {
            HandlerThread thread = new HandlerThread("Camera");
            thread.start();
            cameraHandler = new Handler(thread.getLooper());
        }
        view.setSurfaceTextureListener(this);
    }

    /** Opens the camera, now or as soon as the view is ready. Does nothing if already started. */
    void start() {
        if (started) return;
        started = true;
        SurfaceTexture st = view.isAvailable() ? view.getSurfaceTexture() : null;
        cameraHandler.post(() -> {
            wanted = true;
            if (st != null) texture = st;
            openIfReady();
        });
    }

    /** Closes the camera. The view keeps showing the last picture. */
    void stop() {
        if (!started) return;
        started = false;
        cameraHandler.post(() -> {
            wanted = false;
            close();
        });
    }

    // ------------------------------------------------------------------ texture view

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int width, int height) {
        updateTransform();
        cameraHandler.post(() -> {
            texture = st;
            openIfReady();
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int width, int height) {
        updateTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) {
        // Released only after the camera has stopped writing to it.
        cameraHandler.post(() -> {
            if (texture == st) texture = null;
            close();
            st.release();
        });
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {}

    // ------------------------------------------------------------------ camera thread

    @SuppressLint("MissingPermission")   // MainActivity checks it before start().
    private void openIfReady() {
        if (!wanted || texture == null || camera != null || opening) return;
        CameraManager manager = context.getSystemService(CameraManager.class);
        try {
            String id = chooseCamera(manager);
            if (id == null) {
                fail("This device has no camera");
                return;
            }
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation != null ? orientation : 0;
            previewSize = choosePreviewSize(c);
            continuousFocus = supportsContinuousFocus(c);
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            main.post(this::updateTransform);
            opening = true;
            manager.openCamera(id, deviceCallback, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            Log.w(TAG, "Could not open the camera", e);
            opening = false;
            fail("Could not open the camera");
        }
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cam) {
            opening = false;
            if (!wanted || texture == null) {
                cam.close();
                return;
            }
            camera = cam;
            Surface surface = new Surface(texture);
            try {
                //noinspection deprecation   The list form is the only one before API 28.
                cam.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession s) {
                        if (camera != cam) {
                            s.close();
                            return;
                        }
                        session = s;
                        try {
                            CaptureRequest.Builder request = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            request.addTarget(surface);
                            if (continuousFocus) {
                                request.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            }
                            s.setRepeatingRequest(request.build(), null, cameraHandler);
                        } catch (CameraAccessException | RuntimeException e) {
                            Log.w(TAG, "Could not start the camera picture", e);
                            close();
                            fail("Could not start the camera");
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                        close();
                        fail("Could not start the camera");
                    }
                }, cameraHandler);
            } catch (CameraAccessException | RuntimeException e) {
                Log.w(TAG, "Could not start the camera picture", e);
                close();
                fail("Could not start the camera");
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cam) {
            // Another app took the camera. Opened again on the next start().
            opening = false;
            cam.close();
            if (camera == cam) {
                camera = null;
                session = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cam, int error) {
            Log.w(TAG, "Camera error " + error);
            onDisconnected(cam);
            fail("The camera stopped working");
        }
    };

    private void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    private void fail(String message) {
        main.post(() -> listener.onCameraFailed(message));
    }

    /** A Quest passthrough camera if there is one, otherwise a back camera, otherwise any camera. */
    private static String chooseCamera(CameraManager manager) throws CameraAccessException {
        String back = null, any = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            if (isPassthrough(c)) return id;
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (back == null && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) back = id;
            if (any == null) any = id;
        }
        return back != null ? back : any;
    }

    private static boolean isPassthrough(CameraCharacteristics c) {
        // Keys for vendor tags can only be made from API 29. The Quest is well past that.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            Integer source = c.get(new CameraCharacteristics.Key<>(KEY_CAMERA_SOURCE, Integer.class));
            return source != null && source == CAMERA_SOURCE_PASSTHROUGH;
        } catch (RuntimeException e) {
            return false;   // Not a Quest: the tag does not exist.
        }
    }

    /** The largest size that is not bigger than MAX_PREVIEW_SIDE, or the smallest if all are. */
    private static Size choosePreviewSize(CameraCharacteristics c) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map != null ? map.getOutputSizes(SurfaceTexture.class) : null;
        if (sizes == null || sizes.length == 0) return new Size(1280, 960);
        Size best = null, smallest = sizes[0];
        for (Size s : sizes) {
            long area = (long) s.getWidth() * s.getHeight();
            if (area < (long) smallest.getWidth() * smallest.getHeight()) smallest = s;
            if (Math.max(s.getWidth(), s.getHeight()) > MAX_PREVIEW_SIDE) continue;
            if (best == null || area > (long) best.getWidth() * best.getHeight()) best = s;
        }
        return best != null ? best : smallest;
    }

    private static boolean supportsContinuousFocus(CameraCharacteristics c) {
        int[] modes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (modes == null) return false;
        for (int m : modes) {
            if (m == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ main thread

    /**
     * TextureView stretches the camera picture, already turned upright for the device's natural
     * orientation, over the whole view. This undoes the stretch, turns it for the current screen
     * rotation, and scales it to fill the view without distorting it.
     */
    private void updateTransform() {
        Size size = previewSize;
        int vw = view.getWidth(), vh = view.getHeight();
        if (size == null || vw == 0 || vh == 0) return;

        boolean sideways = sensorOrientation % 180 != 0;
        float cw = sideways ? size.getHeight() : size.getWidth();
        float ch = sideways ? size.getWidth() : size.getHeight();

        Display display = view.getDisplay();
        int rotation = display != null ? display.getRotation() * 90 : 0;
        float sw = rotation % 180 != 0 ? ch : cw;
        float sh = rotation % 180 != 0 ? cw : ch;

        float cx = vw / 2f, cy = vh / 2f;
        float fill = Math.max(vw / sw, vh / sh);
        Matrix m = new Matrix();
        m.setScale(cw / vw, ch / vh, cx, cy);
        m.postRotate(-rotation, cx, cy);
        m.postScale(fill, fill, cx, cy);
        view.setTransform(m);
    }
}
