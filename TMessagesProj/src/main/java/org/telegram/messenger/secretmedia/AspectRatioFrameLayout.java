package org.telegram.messenger.secretmedia;

/**
 * Created by elanimus on 3/28/18.
 */

import android.content.Context;
import android.graphics.Matrix;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A {@link FrameLayout} that resizes itself to match a specified aspect ratio.
 */
public class AspectRatioFrameLayout extends FrameLayout {

    /**
     * Resize modes for {@link AspectRatioFrameLayout}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RESIZE_MODE_FIT, RESIZE_MODE_FIXED_WIDTH, RESIZE_MODE_FIXED_HEIGHT, RESIZE_MODE_FILL,
            RESIZE_MODE_ZOOM})
    public @interface ResizeMode {}

    /**
     * Either the width or height is decreased to obtain the desired aspect ratio.
     */
    public static final int RESIZE_MODE_FIT = 0;
    /**
     * The width is fixed and the height is increased or decreased to obtain the desired aspect ratio.
     */
    public static final int RESIZE_MODE_FIXED_WIDTH = 1;
    /**
     * The height is fixed and the width is increased or decreased to obtain the desired aspect ratio.
     */
    public static final int RESIZE_MODE_FIXED_HEIGHT = 2;
    /**
     * The specified aspect ratio is ignored.
     */
    public static final int RESIZE_MODE_FILL = 3;
    /**
     * Either the width or height is increased to obtain the desired aspect ratio.
     */
    public static final int RESIZE_MODE_ZOOM = 4;

    /**
     * The {@link FrameLayout} will not resize itself if the fractional difference between its natural
     * aspect ratio and the requested aspect ratio falls below this threshold.
     * <p>
     * This tolerance allows the view to occupy the whole of the screen when the requested aspect
     * ratio is very close, but not exactly equal to, the aspect ratio of the screen. This may reduce
     * the number of view layers that need to be composited by the underlying system, which can help
     * to reduce power consumption.
     */
    private static final float MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f;

    private float videoAspectRatio;
    private int resizeMode;
    private boolean drawingReady;
    private int rotation;
    private Matrix matrix = new Matrix();

    public AspectRatioFrameLayout(Context context) {
        this(context, null);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        resizeMode = RESIZE_MODE_FIT;
    }

    /**
     * Sets the aspect ratio that this view should satisfy.
     *
     * @param widthHeightRatio The width to height ratio.
     */
    public boolean isDrawingReady() {
        return drawingReady;
    }

    /**
     * Set the aspect ratio that this view should satisfy.
     *
     * @param widthHeightRatio The width to height ratio.
     */
    public void setAspectRatio(float widthHeightRatio, int rotation) {
        if (this.videoAspectRatio != widthHeightRatio || this.rotation != rotation) {
            this.videoAspectRatio = widthHeightRatio;
            this.rotation = rotation;
            requestLayout();
        }
    }

    /**
     * Returns the resize mode.
     */
    public @ResizeMode int getResizeMode() {
        return resizeMode;
    }

    /**
     * Sets the resize mode.
     *
     * @param resizeMode The resize mode.
     */
    public void setResizeMode(@ResizeMode int resizeMode) {
        if (this.resizeMode != resizeMode) {
            this.resizeMode = resizeMode;
            requestLayout();
        }
    }

    public void setDrawingReady(boolean value) {
        if (drawingReady == value) {
            return;
        }
        drawingReady = value;
    }

    public float getAspectRatio() {
        return videoAspectRatio;
    }

    public int getVideoRotation() {
        return rotation;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (resizeMode == RESIZE_MODE_FILL || videoAspectRatio <= 0) {
            // Aspect ratio not set.
            return;
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        float viewAspectRatio = (float) width / height;
        float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
        if (Math.abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
            // We're within the allowed tolerance.
            return;
        }

        switch (resizeMode) {
            case RESIZE_MODE_FIXED_WIDTH:
                height = (int) (width / videoAspectRatio);
                break;
            case RESIZE_MODE_FIXED_HEIGHT:
                width = (int) (height * videoAspectRatio);
                break;
            case RESIZE_MODE_ZOOM:
                if (aspectDeformation > 0) {
                    width = (int) (height * videoAspectRatio);
                } else {
                    height = (int) (width / videoAspectRatio);
                }
                break;
            default:
                if (aspectDeformation > 0) {
                    height = (int) (width / videoAspectRatio);
                } else {
                    width = (int) (height * videoAspectRatio);
                }
                break;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child instanceof TextureView) {
                matrix.reset();
                int px = getWidth() / 2;
                int py = getHeight() / 2;
                matrix.postRotate(rotation, px, py);
                if (rotation == 90 || rotation == 270) {
                    float ratio = (float) getHeight() / getWidth();
                    matrix.postScale(1 / ratio, ratio, px, py);
                }
                ((TextureView) child).setTransform(matrix);
                break;
            }
        }
    }

}