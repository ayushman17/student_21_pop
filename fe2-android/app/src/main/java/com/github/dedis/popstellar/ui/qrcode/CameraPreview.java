/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.dedis.popstellar.ui.qrcode;

import android.Manifest;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.github.dedis.popstellar.R;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;

import java.io.IOException;

/**
 * View to show the camera preview in a frame.
 *
 * <p>It uses com.google.android.gms.vision.CameraSource to get the camera's image
 */
public class CameraPreview extends ViewGroup {

  private static final String TAG = "CameraPreview";

  private final SurfaceView mSurfaceView;
  private boolean mStartRequested;
  private boolean mSurfaceAvailable;
  private CameraSource mCameraSource;

  public CameraPreview(Context context, AttributeSet attrs) {
    super(context, attrs);
    mStartRequested = false;
    mSurfaceAvailable = false;

    mSurfaceView = new SurfaceView(context);
    mSurfaceView.getHolder().addCallback(new SurfaceCallback());
    addView(mSurfaceView);
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  public void start(CameraSource cameraSource) throws IOException, SecurityException {
    if (cameraSource == null) {
      stop();
    }

    mCameraSource = cameraSource;

    if (mCameraSource != null) {
      mStartRequested = true;
      startIfReady();
    }
  }

  public void stop() {
    if (mCameraSource != null) {
      mCameraSource.stop();
    }
  }

  public void release() {
    if (mCameraSource != null) {
      mCameraSource.release();
      mCameraSource = null;
    }
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  private void startIfReady() throws IOException, SecurityException {
    if (mStartRequested && mSurfaceAvailable) {
      mCameraSource.start(mSurfaceView.getHolder());
      mStartRequested = false;
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int width = getResources().getInteger(R.integer.camera_preview_width);
    int height = getResources().getInteger(R.integer.camera_preview_height);

    if (mCameraSource != null) {
      Size size = mCameraSource.getPreviewSize();
      if (size != null) {
        height = size.getWidth();
        width = size.getHeight();
      }
    }

    final int layoutWidth = right - left;
    final int layoutHeight = bottom - top;

    // Computes height and width for potentially doing fit width.
    int childWidth = layoutWidth;
    int childHeight = (int) (((float) layoutWidth / (float) width) * height);

    // If height is too tall using fit width, does fit height instead.
    if (childHeight > layoutHeight) {
      childHeight = layoutHeight;
      childWidth = (int) (((float) layoutHeight / (float) height) * width);
    }

    for (int i = 0; i < getChildCount(); ++i) {
      getChildAt(i).layout(0, 0, childWidth, childHeight);
    }

    try {
      startIfReady();
    } catch (SecurityException se) {
      Log.e(TAG, "Do not have permission to start the camera", se);
    } catch (IOException e) {
      Log.e(TAG, "Could not start camera source.", e);
    }
  }

  private class SurfaceCallback implements SurfaceHolder.Callback {

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surface) {
      mSurfaceAvailable = true;
      try {
        startIfReady();
      } catch (SecurityException se) {
        Log.e(TAG, "Do not have permission to start the camera", se);
      } catch (IOException e) {
        Log.e(TAG, "Could not start camera source.", e);
      }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surface) {
      mSurfaceAvailable = false;
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
      // Do nothing because the preview doesn't have to change
    }
  }
}
