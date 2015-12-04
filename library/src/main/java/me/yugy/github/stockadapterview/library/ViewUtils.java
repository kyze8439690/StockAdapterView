package me.yugy.github.stockadapterview.library;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

public class ViewUtils {

    public static void dispatchStartTemporaryDetach(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = viewGroup.getChildAt(i);
                dispatchStartTemporaryDetach(child);
            }
        } else {
            view.onStartTemporaryDetach();
        }
    }

    public static void dispatchFinishTemporaryDetach(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = viewGroup.getChildAt(i);
                dispatchFinishTemporaryDetach(child);
            }
        } else {
            view.onFinishTemporaryDetach();
        }
    }

    public static void offsetChildrenTopAndBottom(ViewGroup viewGroup, int offset) {
        final int count = viewGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = viewGroup.getChildAt(i);
            child.offsetTopAndBottom(offset);
        }
    }

    public static void offsetChildrenLeftAndRight(ViewGroup viewGroup, int offset) {
        final int count = viewGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = viewGroup.getChildAt(i);
            child.offsetLeftAndRight(offset);
        }
    }

    public static float getVerticalScrollFactor(View view) {
            TypedValue outValue = new TypedValue();
            if (!view.getContext().getTheme().resolveAttribute(
                    android.R.attr.listPreferredItemHeight, outValue, true)) {
                throw new IllegalStateException(
                        "Expected theme to define listPreferredItemHeight.");
            }
            return outValue.getDimension(view.getResources().getDisplayMetrics());
    }
}
