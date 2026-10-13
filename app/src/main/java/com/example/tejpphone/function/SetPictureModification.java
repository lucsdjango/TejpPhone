package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;

/**
 * Loads a new picture or removes the current one.
 * The desktop "pic1" button could not be undone. Here it can, as can removing a picture.
 */
public class SetPictureModification extends Modification {

    private final Fence fence;
    private final Picture oldPicture, newPicture;

    public SetPictureModification(Fence f, Picture p) {
        fence = f;
        oldPicture = f.getPicture();
        newPicture = p;
    }

    @Override
    public void perform() {
        fence.setPicture(newPicture);
    }

    @Override
    public void revert() {
        fence.setPicture(oldPicture);
    }

    @Override
    public boolean isValid() {
        return oldPicture != newPicture;
    }
}
