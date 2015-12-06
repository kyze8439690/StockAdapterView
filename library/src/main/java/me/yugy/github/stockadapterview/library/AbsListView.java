package me.yugy.github.stockadapterview.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.support.v4.widget.ScrollerCompat;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "deprecation"})
public abstract class AbsListView extends AdapterView<ListAdapter>
        implements ViewTreeObserver.OnTouchModeChangeListener {
    public static final int TRANSCRIPT_MODE_DISABLED = 0;
    public static final int TRANSCRIPT_MODE_NORMAL = 1;
    public static final int TRANSCRIPT_MODE_ALWAYS_SCROLL = 2;

    private int mTranscriptMode;

    static final int TOUCH_MODE_REST = -1;
    static final int TOUCH_MODE_DOWN = 0;
    static final int TOUCH_MODE_TAP = 1;
    static final int TOUCH_MODE_DONE_WAITING = 2;
    static final int TOUCH_MODE_SCROLL = 3;
    static final int TOUCH_MODE_FLING = 4;
    static final int TOUCH_MODE_OVERSCROLL = 5;
    static final int TOUCH_MODE_OVERFLING = 6;

    int mTouchMode = TOUCH_MODE_REST;

    static final int LAYOUT_NORMAL = 0;
    static final int LAYOUT_FORCE_TOP = 1;
    static final int LAYOUT_SET_SELECTION = 2;
    static final int LAYOUT_FORCE_BOTTOM = 3;
    static final int LAYOUT_SPECIFIC = 4;
    static final int LAYOUT_SYNC = 5;
    static final int LAYOUT_MOVE_SELECTION = 6;

    int mLayoutMode = LAYOUT_NORMAL;

    AdapterDataSetObserver mDataSetObserver;

    ListAdapter mAdapter;

    boolean mDrawSelectorOnTop = false;

    Drawable mSelector;

    int mSelectorPosition = INVALID_POSITION;

    Rect mSelectorRect = new Rect();

    final RecycleBin mRecycler = new RecycleBin();

    int mSelectionLeftPadding = 0;
    int mSelectionTopPadding = 0;
    int mSelectionRightPadding = 0;
    int mSelectionBottomPadding = 0;

    Rect mListPadding = new Rect();

    int mWidthMeasureSpec = 0;

    boolean mCachingStarted;

    int mMotionPosition;

    int mMotionViewOriginalTop;
    int mMotionViewNewTop;

    int mMotionX;
    int mMotionY;

    int mLastY;

    int mMotionCorrection;

    private VelocityTracker mVelocityTracker;

    private FlingRunnable mFlingRunnable;

    private PositionScroller mPositionScroller;

    int mSelectedTop = 0;

    boolean mStackFromBottom;

    boolean mScrollingCacheEnabled;

    private OnScrollListener mOnScrollListener;

    private boolean mSmoothScrollbarEnabled = true;

    private Rect mTouchFrame;

    int mResurrectToPosition = INVALID_POSITION;

    private ContextMenu.ContextMenuInfo mContextMenuInfo = null;

    private static final int TOUCH_MODE_UNKNOWN = -1;
    private static final int TOUCH_MODE_ON = 0;
    private static final int TOUCH_MODE_OFF = 1;

    private int mLastTouchMode = TOUCH_MODE_UNKNOWN;

    private CheckForLongPress mPendingCheckForLongPress;

    private CheckForTap mPendingCheckForTap;

    private CheckForKeyLongPress mPendingCheckForKeyLongPress;

    private AbsListView.PerformClick mPerformClick;

    private int mCacheColorHint;

    private boolean mIsChildViewEnabled;

    private int mLastScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    private int mTouchSlop;

    private Runnable mClearScrollingCache;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    final boolean[] mIsScrap = new boolean[1];

    private int mActivePointerId = INVALID_POINTER;

    private static final int INVALID_POINTER = -1;

    int mOverscrollDistance;

    private boolean mClipToPadding;
    private boolean mDisallowIntercept;

    private EdgeEffectCompat mEdgeGlowTop;
    private EdgeEffectCompat mEdgeGlowBottom;

    private int mFirstPositionDistanceGuess;
    private int mLastPositionDistanceGuess;

    private int mDirection = 0;

    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChanged(AbsListView view, int scrollState);

        void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                      int totalItemCount);
    }

    public AbsListView(Context context) {
        super(context);
        initAbsListView();
        setVerticalScrollBarEnabled(true);
    }

    public AbsListView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.absListViewStyle);
    }

    public AbsListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAbsListView();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AbsListView, defStyle, 0);

        Drawable d = a.getDrawable(R.styleable.AbsListView_android_listSelector);
        if (d != null) {
            setSelector(d);
        }

        mDrawSelectorOnTop = a.getBoolean(R.styleable.AbsListView_android_drawSelectorOnTop, false);

        boolean stackFromBottom = a.getBoolean(R.styleable.AbsListView_android_stackFromBottom, false);
        setStackFromBottom(stackFromBottom);

        boolean scrollingCacheEnabled = a.getBoolean(R.styleable.AbsListView_android_scrollingCache, true);
        setScrollingCacheEnabled(scrollingCacheEnabled);

        int transcriptMode = a.getInt(R.styleable.AbsListView_android_transcriptMode, TRANSCRIPT_MODE_DISABLED);
        setTranscriptMode(transcriptMode);

        int color = a.getColor(R.styleable.AbsListView_android_cacheColorHint, 0);
        setCacheColorHint(color);

        boolean smoothScrollbar = a.getBoolean(R.styleable.AbsListView_android_smoothScrollbar, true);
        setSmoothScrollbarEnabled(smoothScrollbar);

        a.recycle();
    }

    private void initAbsListView() {
        // Setting focusable in touch mode will set the focusable property to true
        setClickable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);
        setAlwaysDrawnWithCacheEnabled(false);
        setScrollingCacheEnabled(true);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (overScrollMode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffectCompat(context);
                mEdgeGlowBottom = new EdgeEffectCompat(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
        super.setOverScrollMode(overScrollMode);
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        super.setClipToPadding(clipToPadding);
        mClipToPadding = clipToPadding;
        invalidate();
    }

    public boolean isClipToPadding() {
        return mClipToPadding;
    }

    private boolean contentFits() {
        final int childCount = getChildCount();
        //noinspection SimplifiableIfStatement
        if (childCount != mItemCount) {
            return false;
        }

        return getChildAt(0).getTop() >= 0 && getChildAt(childCount - 1).getBottom() <= getBottom();
    }

    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    public boolean isSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    public void setOnScrollListener(OnScrollListener l) {
        mOnScrollListener = l;
        invokeOnItemScrollListener();
    }

    void invokeOnItemScrollListener() {
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(this, mFirstPosition, getChildCount(), mItemCount);
        }
    }

    public boolean isScrollingCacheEnabled() {
        return mScrollingCacheEnabled;
    }

    public void setScrollingCacheEnabled(boolean enabled) {
        if (mScrollingCacheEnabled && !enabled) {
            clearScrollingCache();
        }
        mScrollingCacheEnabled = enabled;
    }

    @Override
    public void getFocusedRect(Rect r) {
        View view = getSelectedView();
        if (view != null && view.getParent() == this) {
            // the focused rectangle of the selected view offset into the
            // coordinate space of this view.
            view.getFocusedRect(r);
            offsetDescendantRectToMyCoords(view, r);
        } else {
            // otherwise, just the norm
            super.getFocusedRect(r);
        }
    }

    private void useDefaultSelector() {
        setSelector(android.R.drawable.list_selector_background);
    }

    public boolean isStackFromBottom() {
        return mStackFromBottom;
    }

    public void setStackFromBottom(boolean stackFromBottom) {
        if (mStackFromBottom != stackFromBottom) {
            mStackFromBottom = stackFromBottom;
            requestLayoutIfNecessary();
        }
    }

    void requestLayoutIfNecessary() {
        if (getChildCount() > 0) {
            resetList();
            requestLayout();
            invalidate();
        }
    }

    static class SavedState extends BaseSavedState {
        long selectedId;
        long firstId;
        int viewTop;
        int position;
        int height;

        /**
         * Constructor called from {@link AbsListView#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            selectedId = in.readLong();
            firstId = in.readLong();
            viewTop = in.readInt();
            position = in.readInt();
            height = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeLong(selectedId);
            out.writeLong(firstId);
            out.writeInt(viewTop);
            out.writeInt(position);
            out.writeInt(height);
        }

        @Override
        public String toString() {
            return "AbsListView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " selectedId=" + selectedId
                    + " firstId=" + firstId
                    + " viewTop=" + viewTop
                    + " position=" + position
                    + " height=" + height + "}";
        }

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {

        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        boolean haveChildren = getChildCount() > 0 && mItemCount > 0;
        long selectedId = getSelectedItemId();
        ss.selectedId = selectedId;
        ss.height = getHeight();

        if (selectedId >= 0) {
            // Remember the selection
            ss.viewTop = mSelectedTop;
            ss.position = getSelectedItemPosition();
            ss.firstId = INVALID_POSITION;
        } else {
            if (haveChildren && mFirstPosition > 0) {
                // Remember the position of the first child.
                // We only do this if we are not currently at the top of
                // the list, for two reasons:
                // (1) The list may be in the process of becoming empty, in
                // which case mItemCount may not be 0, but if we try to
                // ask for any information about position 0 we will crash.
                // (2) Being "at the top" seems like a special case, anyway,
                // and the user wouldn't expect to end up somewhere else when
                // they revisit the list even if its content has changed.
                View v = getChildAt(0);
                ss.viewTop = v.getTop();
                int firstPos = mFirstPosition;
                if (firstPos >= mItemCount) {
                    firstPos = mItemCount - 1;
                }
                ss.position = firstPos;
                ss.firstId = mAdapter.getItemId(firstPos);
            } else {
                ss.viewTop = 0;
                ss.firstId = INVALID_POSITION;
                ss.position = 0;
            }
        }

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;

        super.onRestoreInstanceState(ss.getSuperState());
        mDataChanged = true;

        mSyncHeight = ss.height;

        if (ss.selectedId >= 0) {
            mNeedSync = true;
            mSyncRowId = ss.selectedId;
            mSyncPosition = ss.position;
            mSpecificTop = ss.viewTop;
            mSyncMode = SYNC_SELECTED_POSITION;
        } else if (ss.firstId >= 0) {
            setSelectedPositionInt(INVALID_POSITION);
            // Do this before setting mNeedSync since setNextSelectedPosition looks at mNeedSync
            setNextSelectedPositionInt(INVALID_POSITION);
            mSelectorPosition = INVALID_POSITION;
            mNeedSync = true;
            mSyncRowId = ss.firstId;
            mSyncPosition = ss.position;
            mSpecificTop = ss.viewTop;
            mSyncMode = SYNC_FIRST_POSITION;
        }

        requestLayout();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && mSelectedPosition < 0 && !isInTouchMode()) {
            resurrectSelection();
        }
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests && !mInLayout) {
            super.requestLayout();
        }
    }

    void resetList() {
        removeAllViewsInLayout();
        mFirstPosition = 0;
        mDataChanged = false;
        mNeedSync = false;
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        setSelectedPositionInt(INVALID_POSITION);
        setNextSelectedPositionInt(INVALID_POSITION);
        mSelectedTop = 0;
        mSelectorPosition = INVALID_POSITION;
        mSelectorRect.setEmpty();
        invalidate();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        final int count = getChildCount();
        if (count > 0) {
            if (mSmoothScrollbarEnabled) {
                int extent = count * 100;

                View view = getChildAt(0);
                final int top = view.getTop();
                int height = view.getHeight();
                if (height > 0) {
                    extent += (top * 100) / height;
                }

                view = getChildAt(count - 1);
                final int bottom = view.getBottom();
                height = view.getHeight();
                if (height > 0) {
                    extent -= ((bottom - getHeight()) * 100) / height;
                }

                return extent;
            } else {
                return 1;
            }
        }
        return 0;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        final int firstPosition = mFirstPosition;
        final int childCount = getChildCount();
        if (firstPosition >= 0 && childCount > 0) {
            if (mSmoothScrollbarEnabled) {
                final View view = getChildAt(0);
                final int top = view.getTop();
                int height = view.getHeight();
                if (height > 0) {
                    return Math.max(firstPosition * 100 - (top * 100) / height +
                            (int) ((float) getScrollY() / getHeight() * mItemCount * 100), 0);
                }
            } else {
                int index;
                final int count = mItemCount;
                if (firstPosition == 0) {
                    index = 0;
                } else if (firstPosition + childCount == count) {
                    index = count;
                } else {
                    index = firstPosition + childCount / 2;
                }
                return (int) (firstPosition + childCount * (index / (float) count));
            }
        }
        return 0;
    }

    @Override
    protected int computeVerticalScrollRange() {
        int result;
        if (mSmoothScrollbarEnabled) {
            result = Math.max(mItemCount * 100, 0);
            if (getScrollY() != 0) {
                // Compensate for overscroll
                result += Math.abs((int) ((float) getScrollY() / getHeight() * mItemCount * 100));
            }
        } else {
            result = mItemCount;
        }
        return result;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        final int count = getChildCount();
        final float fadeEdge = super.getTopFadingEdgeStrength();
        if (count == 0) {
            return fadeEdge;
        } else {
            if (mFirstPosition > 0) {
                return 1.0f;
            }

            final int top = getChildAt(0).getTop();
            final float fadeLength = (float) getVerticalFadingEdgeLength();
            return top < getPaddingTop() ? (float) -(top - getPaddingTop()) / fadeLength : fadeEdge;
        }
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        final int count = getChildCount();
        final float fadeEdge = super.getBottomFadingEdgeStrength();
        if (count == 0) {
            return fadeEdge;
        } else {
            if (mFirstPosition + count - 1 < mItemCount - 1) {
                return 1.0f;
            }

            final int bottom = getChildAt(count - 1).getBottom();
            final int height = getHeight();
            final float fadeLength = (float) getVerticalFadingEdgeLength();
            return bottom > height - getPaddingBottom() ?
                    (float) (bottom - height + getPaddingBottom()) / fadeLength : fadeEdge;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mSelector == null) {
            useDefaultSelector();
        }
        final Rect listPadding = mListPadding;
        listPadding.left = mSelectionLeftPadding + getPaddingLeft();
        listPadding.top = mSelectionTopPadding + getPaddingTop();
        listPadding.right = mSelectionRightPadding + getPaddingRight();
        listPadding.bottom = mSelectionBottomPadding + getPaddingBottom();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInLayout = true;
        if (changed) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).forceLayout();
            }
            mRecycler.markChildrenDirty();
        }

        layoutChildren();
        mInLayout = false;
    }

    protected void layoutChildren() {
    }

    public View getSelectedView() {
        if (mItemCount > 0 && mSelectedPosition >= 0) {
            return getChildAt(mSelectedPosition - mFirstPosition);
        } else {
            return null;
        }
    }

    public int getListPaddingTop() {
        return mListPadding.top;
    }

    public int getListPaddingBottom() {
        return mListPadding.bottom;
    }

    public int getListPaddingLeft() {
        return mListPadding.left;
    }

    public int getListPaddingRight() {
        return mListPadding.right;
    }

    View obtainView(int position, boolean[] isScrap) {
        isScrap[0] = false;
        View scrapView;

        scrapView = mRecycler.getScrapView(position);

        View child;
        if (scrapView != null) {
            child = mAdapter.getView(position, scrapView, this);
            if (child != scrapView) {
                mRecycler.addScrapView(scrapView);
                if (mCacheColorHint != 0) {
                    child.setDrawingCacheBackgroundColor(mCacheColorHint);
                }
            } else {
                isScrap[0] = true;
                ViewUtils.dispatchFinishTemporaryDetach(child);
            }
        } else {
            child = mAdapter.getView(position, null, this);
            if (mCacheColorHint != 0) {
                child.setDrawingCacheBackgroundColor(mCacheColorHint);
            }
        }

        return child;
    }

    void positionSelector(int position, View sel) {
        final boolean positionChanged = position != mSelectorPosition;
        if (position != INVALID_POSITION) {
            mSelectorPosition = position;
        }

        final Rect selectorRect = mSelectorRect;
        selectorRect.set(sel.getLeft(), sel.getTop(), sel.getRight(), sel.getBottom());
        positionSelector(selectorRect.left, selectorRect.top, selectorRect.right,
                selectorRect.bottom);

        final boolean isChildViewEnabled = sel.isEnabled();
        if (mIsChildViewEnabled != isChildViewEnabled) {
            mIsChildViewEnabled = isChildViewEnabled;
        }

        final Drawable selector = mSelector;
        if (selector != null) {
            if (positionChanged) {
                // Wipe out the current selector state so that we can start
                // over in the new position with a fresh state.
                selector.setVisible(false, false);
                selector.setState(new int[]{0});
            }
            selector.setBounds(selectorRect);
            if (positionChanged) {
                if (getVisibility() == VISIBLE) {
                    selector.setVisible(true, false);
                }
                updateSelectorState();
            }
        }
    }

    private void positionSelector(int l, int t, int r, int b) {
        mSelectorRect.set(l - mSelectionLeftPadding, t - mSelectionTopPadding, r
                + mSelectionRightPadding, b + mSelectionBottomPadding);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int saveCount = 0;
        if (isClipToPadding()) {
            saveCount = canvas.save();
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.clipRect(scrollX + getPaddingLeft(), scrollY + getPaddingTop(),
                    scrollX + getRight() - getLeft() - getPaddingRight(),
                    scrollY + getBottom() - getTop() - getPaddingBottom());
        }

        final boolean drawSelectorOnTop = mDrawSelectorOnTop;
        if (!drawSelectorOnTop) {
            drawSelector(canvas);
        }

        super.dispatchDraw(canvas);

        if (drawSelectorOnTop) {
            drawSelector(canvas);
        }

        if (isClipToPadding()) {
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    protected int getLeftPaddingOffset() {
        return getPaddingLeft();
    }

    @Override
    protected int getTopPaddingOffset() {
        return getPaddingTop();
    }

    @Override
    protected int getRightPaddingOffset() {
        return getPaddingRight();
    }

    @Override
    protected int getBottomPaddingOffset() {
        return getPaddingBottom();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (getChildCount() > 0) {
            mDataChanged = true;
            rememberSyncState();
        }
    }

    boolean touchModeDrawsInPressedState() {
        switch (mTouchMode) {
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                return true;
            default:
                return false;
        }
    }

    boolean shouldShowSelector() {
        return (isFocused() && !isInTouchMode()) || (touchModeDrawsInPressedState() && isPressed());
    }

    private void drawSelector(Canvas canvas) {
        if (mSelectorRect != null && !mSelectorRect.isEmpty()) {
            final Drawable selector = mSelector;
            selector.setBounds(mSelectorRect);
            selector.draw(canvas);
        }
    }

    public void setDrawSelectorOnTop(boolean onTop) {
        mDrawSelectorOnTop = onTop;
    }

    public void setSelector(int resID) {
        setSelector(ResourcesCompat.getDrawable(getResources(), resID, getContext().getTheme()));
    }

    public void setSelector(Drawable sel) {
        if (mSelector != null) {
            mSelector.setCallback(null);
            unscheduleDrawable(mSelector);
        }
        mSelector = sel;
        Rect padding = new Rect();
        sel.getPadding(padding);
        mSelectionLeftPadding = padding.left;
        mSelectionTopPadding = padding.top;
        mSelectionRightPadding = padding.right;
        mSelectionBottomPadding = padding.bottom;
        sel.setCallback(this);
        updateSelectorState();
    }

    public Drawable getSelector() {
        return mSelector;
    }

    void keyPressed() {
        if (!isEnabled() || !isClickable()) {
            return;
        }

        Drawable selector = mSelector;
        Rect selectorRect = mSelectorRect;
        if (selector != null && (isFocused() || touchModeDrawsInPressedState())
                && selectorRect != null && !selectorRect.isEmpty()) {

            final View v = getChildAt(mSelectedPosition - mFirstPosition);

            if (v != null) {
                if (v.hasFocusable()) return;
                v.setPressed(true);
            }
            setPressed(true);

            final boolean longClickable = isLongClickable();
            Drawable d = selector.getCurrent();
            if (d != null && d instanceof TransitionDrawable) {
                if (longClickable) {
                    ((TransitionDrawable) d).startTransition(
                            ViewConfiguration.getLongPressTimeout());
                } else {
                    ((TransitionDrawable) d).resetTransition();
                }
            }
            if (longClickable && !mDataChanged) {
                if (mPendingCheckForKeyLongPress == null) {
                    mPendingCheckForKeyLongPress = new CheckForKeyLongPress();
                }
                mPendingCheckForKeyLongPress.rememberWindowAttachCount();
                postDelayed(mPendingCheckForKeyLongPress, ViewConfiguration.getLongPressTimeout());
            }
        }
    }

    void updateSelectorState() {
        if (mSelector != null) {
            if (shouldShowSelector()) {
                mSelector.setState(getDrawableStateForSelector());
            } else {
                mSelector.setState(new int[]{0});
            }
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateSelectorState();
    }

    private int[] getDrawableStateForSelector() {
        // If the child view is enabled then do the default behavior.
        if (mIsChildViewEnabled) {
            // Common case
            return super.getDrawableState();
        }

        // The selector uses this View's drawable state. The selected child view
        // is disabled, so we need to remove the enabled state from the drawable
        // states.
        final int enabledState = ENABLED_STATE_SET[0];

        // If we don't have any extra space, it will return one of the static
        // state arrays, and clearing the enabled state on those arrays is a
        // bad thing! If we specify we need extra space, it will create+copy
        // into a new array that is safely mutable.
        final int[] state = onCreateDrawableState(1);

        int enabledPos = -1;
        for (int i = state.length - 1; i >= 0; i--) {
            if (state[i] == enabledState) {
                enabledPos = i;
                break;
            }
        }

        // Remove the enabled state
        if (enabledPos >= 0) {
            System.arraycopy(state, enabledPos + 1, state, enabledPos,
                    state.length - enabledPos - 1);
        }

        return state;
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mSelector != null) DrawableCompat.jumpToCurrentState(mSelector);
    }

    @Override
    public boolean verifyDrawable(Drawable dr) {
        return mSelector == dr || super.verifyDrawable(dr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final ViewTreeObserver treeObserver = getViewTreeObserver();
        if (treeObserver != null) {
            treeObserver.addOnTouchModeChangeListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mRecycler.clear();

        final ViewTreeObserver treeObserver = getViewTreeObserver();
        if (treeObserver != null) {
            treeObserver.removeOnTouchModeChangeListener(this);
        }
    }

    @Override
    public void setScrollY(int y) {
        scrollTo(getScrollX(), y);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        final int touchMode = isInTouchMode() ? TOUCH_MODE_ON : TOUCH_MODE_OFF;

        if (!hasWindowFocus) {
            setChildrenDrawingCacheEnabled(false);
            if (mFlingRunnable != null) {
                removeCallbacks(mFlingRunnable);
                // let the fling runnable report it's new state which
                // should be idle
                mFlingRunnable.endFling();
                if (getScrollY() != 0) {
                    setScrollY(0);
                    finishGlows();
                    invalidate();
                }
            }

            if (touchMode == TOUCH_MODE_OFF) {
                // Remember the last selected element
                mResurrectToPosition = mSelectedPosition;
            }
        } else {
            // If we changed touch mode since the last time we had focus
            if (touchMode != mLastTouchMode && mLastTouchMode != TOUCH_MODE_UNKNOWN) {
                // If we come back in trackball mode, we bring the selection back
                if (touchMode == TOUCH_MODE_OFF) {
                    // This will trigger a layout
                    resurrectSelection();

                    // If we come back in touch mode, then we want to hide the selector
                } else {
                    hideSelector();
                    mLayoutMode = LAYOUT_NORMAL;
                    layoutChildren();
                }
            }
        }

        mLastTouchMode = touchMode;
    }

    ContextMenu.ContextMenuInfo createContextMenuInfo(View view, int position, long id) {
        return new AdapterContextMenuInfo(view, position, id);
    }

    @Override
    public void onCancelPendingInputEvents() {
        super.onCancelPendingInputEvents();
        Handler handler = getHandler();
        if (handler != null) {
            if (mPerformClick != null) {
                handler.removeCallbacks(mPerformClick);
            }
            if (mPendingCheckForTap != null) {
                handler.removeCallbacks(mPendingCheckForTap);
            }
            if (mPendingCheckForLongPress != null) {
                handler.removeCallbacks(mPendingCheckForLongPress);
            }
            if (mPendingCheckForKeyLongPress != null) {
                handler.removeCallbacks(mPendingCheckForKeyLongPress);
            }
        }
    }

    private class WindowRunnable {
        private int mOriginalAttachCount;

        public void rememberWindowAttachCount() {
            mOriginalAttachCount = getWindowAttachCount();
        }

        public boolean sameWindow() {
            return hasWindowFocus() && getWindowAttachCount() == mOriginalAttachCount;
        }
    }

    private class PerformClick extends WindowRunnable implements Runnable {
        View mChild;
        int mClickMotionPosition;

        @Override
        public void run() {
            // The data has changed since we posted this action in the event queue,
            // bail out before bad things happen
            if (mDataChanged) return;

            final ListAdapter adapter = mAdapter;
            final int motionPosition = mClickMotionPosition;
            if (adapter != null && mItemCount > 0 &&
                    motionPosition != INVALID_POSITION &&
                    motionPosition < adapter.getCount() && sameWindow()) {
                performItemClick(mChild, motionPosition, adapter.getItemId(motionPosition));
            }
        }
    }

    private class CheckForLongPress extends WindowRunnable implements Runnable {
        @Override
        public void run() {
            final int motionPosition = mMotionPosition;
            final View child = getChildAt(motionPosition - mFirstPosition);
            if (child != null) {
                final int longPressPosition = mMotionPosition;
                final long longPressId = mAdapter.getItemId(mMotionPosition);

                boolean handled = false;
                if (sameWindow() && !mDataChanged) {
                    handled = performLongPress(child, longPressPosition, longPressId);
                }
                if (handled) {
                    mTouchMode = TOUCH_MODE_REST;
                    setPressed(false);
                    child.setPressed(false);
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }

    private class CheckForKeyLongPress extends WindowRunnable implements Runnable {
        @Override
        public void run() {
            if (isPressed() && mSelectedPosition >= 0) {
                int index = mSelectedPosition - mFirstPosition;
                View v = getChildAt(index);

                if (!mDataChanged) {
                    boolean handled = false;
                    if (sameWindow()) {
                        handled = performLongPress(v, mSelectedPosition, mSelectedRowId);
                    }
                    if (handled) {
                        setPressed(false);
                        v.setPressed(false);
                    }
                } else {
                    setPressed(false);
                    if (v != null) v.setPressed(false);
                }
            }
        }
    }

    private boolean performLongPress(final View child,
                                     final int longPressPosition, final long longPressId) {
        boolean handled = false;

        if (mOnItemLongClickListener != null) {
            handled = mOnItemLongClickListener.onItemLongClick(AbsListView.this, child,
                    longPressPosition, longPressId);
        }
        if (!handled) {
            mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
            handled = super.showContextMenuForChild(AbsListView.this);
        }
        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        return handled;
    }

    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        final int longPressPosition = getPositionForView(originalView);
        if (longPressPosition >= 0) {
            final long longPressId = mAdapter.getItemId(longPressPosition);
            boolean handled = false;

            if (mOnItemLongClickListener != null) {
                handled = mOnItemLongClickListener.onItemLongClick(AbsListView.this, originalView,
                        longPressPosition, longPressId);
            }
            if (!handled) {
                mContextMenuInfo = createContextMenuInfo(
                        getChildAt(longPressPosition - mFirstPosition),
                        longPressPosition, longPressId);
                handled = super.showContextMenuForChild(originalView);
            }

            return handled;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!isEnabled()) {
                    return true;
                }
                if (isClickable() && isPressed() &&
                        mSelectedPosition >= 0 && mAdapter != null &&
                        mSelectedPosition < mAdapter.getCount()) {

                    final View view = getChildAt(mSelectedPosition - mFirstPosition);
                    if (view != null) {
                        performItemClick(view, mSelectedPosition, mSelectedRowId);
                        view.setPressed(false);
                    }
                    setPressed(false);
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // Don't dispatch setPressed to our children. We call setPressed on ourselves to
        // get the selector in the right state, but we don't want to press each child.
    }

    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }

        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    public long pointToRowId(int x, int y) {
        int position = pointToPosition(x, y);
        if (position >= 0) {
            return mAdapter.getItemId(position);
        }
        return INVALID_ROW_ID;
    }

    final class CheckForTap implements Runnable {

        float x, y;

        public void run() {
            if (mTouchMode == TOUCH_MODE_DOWN) {
                mTouchMode = TOUCH_MODE_TAP;
                final View child = getChildAt(mMotionPosition - mFirstPosition);
                if (child != null && !child.hasFocusable()) {
                    mLayoutMode = LAYOUT_NORMAL;

                    if (!mDataChanged) {
                        layoutChildren();
                        child.setPressed(true);
                        positionSelector(mMotionPosition, child);
                        setPressed(true);
                        refreshDrawableState();

                        final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
                        final boolean longClickable = isLongClickable();

                        if (mSelector != null) {
                            Drawable d = mSelector.getCurrent();
                            if (d != null && d instanceof TransitionDrawable) {
                                if (longClickable) {
                                    ((TransitionDrawable) d).startTransition(longPressTimeout);
                                } else {
                                    ((TransitionDrawable) d).resetTransition();
                                }
                            }
                            DrawableCompat.setHotspot(mSelector, x, y);
                        }

                        if (longClickable) {
                            if (mPendingCheckForLongPress == null) {
                                mPendingCheckForLongPress = new CheckForLongPress();
                            }
                            mPendingCheckForLongPress.rememberWindowAttachCount();
                            postDelayed(mPendingCheckForLongPress, longPressTimeout);
                        } else {
                            mTouchMode = TOUCH_MODE_DONE_WAITING;
                        }
                    } else {
                        mTouchMode = TOUCH_MODE_DONE_WAITING;
                    }
                }
            }
        }
    }

    private boolean startScrollIfNeeded(int deltaY) {
        // Check if we have moved far enough that it looks more like a
        // scroll than a tap
        final int distance = Math.abs(deltaY);
        final boolean overscroll = getScrollY() != 0;
        if (distance > mTouchSlop) {
            createScrollingCache();
            mTouchMode = overscroll ? TOUCH_MODE_OVERSCROLL : TOUCH_MODE_SCROLL;
            mMotionCorrection = deltaY;
            final Handler handler = getHandler();
            // Handler should not be null unless the AbsListView is not attached to a
            // window, which would make it very hard to scroll it... but the monkeys
            // say it's possible.
            if (handler != null) {
                handler.removeCallbacks(mPendingCheckForLongPress);
            }
            setPressed(false);
            View motionView = getChildAt(mMotionPosition - mFirstPosition);
            if (motionView != null) {
                motionView.setPressed(false);
            }
            reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
            // Time to start stealing events! Once we've stolen them, don't let anyone
            // steal from us
            requestDisallowInterceptTouchEvent(true);
            return true;
        }

        return false;
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
        if (isInTouchMode) {
            // Get rid of the selection when we enter touch mode
            hideSelector();
            // Layout, but only if we already have done so previously.
            // (Otherwise may clobber a LAYOUT_SYNC layout that was requested to restore
            // state.)
            if (getHeight() > 0 && getChildCount() > 0) {
                // We do not lose focus initiating a touch (since AbsListView is focusable in
                // touch mode). Force an initial layout to get rid of the selection.
                layoutChildren();
            }
            updateSelectorState();
        } else {
            int touchMode = mTouchMode;
            if (touchMode == TOUCH_MODE_OVERSCROLL || touchMode == TOUCH_MODE_OVERFLING) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.endFling();
                }

                if (getScrollY() != 0) {
                    setScrollY(0);
                    finishGlows();
                    invalidate();
                }
            }
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        mDisallowIntercept = disallowIntercept;
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            return isClickable() || isLongClickable();
        }

        final int action = ev.getAction();

        View v;
        int deltaY;

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                switch (mTouchMode) {
                    case TOUCH_MODE_OVERFLING: {
                        mFlingRunnable.endFling();
                        mTouchMode = TOUCH_MODE_OVERSCROLL;
                        mMotionY = mLastY = (int) ev.getY();
                        mMotionCorrection = 0;
                        mActivePointerId = ev.getPointerId(0);
                        break;
                    }
                    default: {
                        mActivePointerId = ev.getPointerId(0);
                        final int x = (int) ev.getX();
                        final int y = (int) ev.getY();
                        int motionPosition = pointToPosition(x, y);
                        if (!mDataChanged) {
                            if ((mTouchMode != TOUCH_MODE_FLING) && (motionPosition >= 0)
                                    && (getAdapter().isEnabled(motionPosition))) {
                                // User clicked on an actual view (and was not stopping a fling). It might be a
                                // click or a scroll. Assume it is a click until proven otherwise
                                mTouchMode = TOUCH_MODE_DOWN;
                                if (mPendingCheckForTap == null) {
                                    mPendingCheckForTap = new CheckForTap();
                                }
                                mPendingCheckForTap.x = x;
                                mPendingCheckForTap.y = y;
                                postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                            } else {
                                if (ev.getEdgeFlags() != 0 && motionPosition < 0) {
                                    // If we couldn't find a view to click on, but the down event was touching
                                    // the edge, we will bail out and try again. This allows the edge correcting
                                    // code in ViewRoot to try to find a nearby view to select
                                    return false;
                                }

                                if (mTouchMode == TOUCH_MODE_FLING) {
                                    // Stopped a fling. It is a scroll.
                                    createScrollingCache();
                                    mTouchMode = TOUCH_MODE_SCROLL;
                                    mMotionCorrection = 0;
                                    motionPosition = findMotionRow(y);
                                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                                }
                            }
                        }

                        if (motionPosition >= 0) {
                            // Remember where the motion event started
                            v = getChildAt(motionPosition - mFirstPosition);
                            mMotionViewOriginalTop = v.getTop();
                        }
                        mMotionX = x;
                        mMotionY = y;
                        mMotionPosition = motionPosition;
                        mLastY = Integer.MIN_VALUE;
                        break;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final int x = (int) ev.getX(pointerIndex);
                final int y = (int) ev.getY(pointerIndex);
                deltaY = y - mMotionY;
                switch (mTouchMode) {
                    case TOUCH_MODE_DOWN:
                    case TOUCH_MODE_TAP:
                    case TOUCH_MODE_DONE_WAITING:
                        // Check if we have moved far enough that it looks more like a
                        // scroll than a tap
                        startScrollIfNeeded(deltaY);
                        break;
                    case TOUCH_MODE_SCROLL:
                        if (y != mLastY) {
                            // We may be here after stopping a fling and continuing to scroll.
                            // If so, we haven't disallowed intercepting touch events yet.
                            // Make sure that we do so in case we're in a parent that can intercept.
                            if (!mDisallowIntercept && Math.abs(deltaY) > mTouchSlop) {
                                requestDisallowInterceptTouchEvent(true);
                            }

                            final int rawDeltaY = deltaY;
                            deltaY -= mMotionCorrection;
                            int incrementalDeltaY = mLastY != Integer.MIN_VALUE ? y - mLastY : deltaY;

                            final int motionIndex;
                            if (mMotionPosition >= 0) {
                                motionIndex = mMotionPosition - mFirstPosition;
                            } else {
                                // If we don't have a motion position that we can reliably track,
                                // pick something in the middle to make a best guess at things below.
                                motionIndex = getChildCount() / 2;
                            }

                            int motionViewPrevTop = 0;
                            View motionView = this.getChildAt(motionIndex);
                            if (motionView != null) {
                                motionViewPrevTop = motionView.getTop();
                            }

                            // No need to do all this work if we're not going to move anyway
                            boolean atEdge = false;
                            if (incrementalDeltaY != 0) {
                                atEdge = trackMotionScroll(deltaY, incrementalDeltaY);
                            }

                            // Check to see if we have bumped into the scroll limit
                            motionView = this.getChildAt(motionIndex);
                            if (motionView != null) {
                                final int motionViewRealTop = motionView.getTop();
                                if (atEdge) {
                                    // Apply overscroll

                                    int overscroll = -incrementalDeltaY -
                                            (motionViewRealTop - motionViewPrevTop);
                                    overScrollBy(0, overscroll, 0, getScrollY(), 0, 0,
                                            0, mOverscrollDistance, true);
                                    if (Math.abs(mOverscrollDistance) == Math.abs(getScrollY())) {
                                        // Don't allow overfling if we're at the edge.
                                        mVelocityTracker.clear();
                                    }

                                    final int overscrollMode = getOverScrollMode();
                                    if (overscrollMode == OVER_SCROLL_ALWAYS ||
                                            (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS &&
                                                    !contentFits())) {
                                        mDirection = 0; // Reset when entering overscroll.
                                        mTouchMode = TOUCH_MODE_OVERSCROLL;
                                        if (rawDeltaY > 0) {
                                            mEdgeGlowTop.onPull((float) overscroll / getHeight(),
                                                    (float) x / getWidth());
                                            if (!mEdgeGlowBottom.isFinished()) {
                                                mEdgeGlowBottom.onRelease();
                                            }
                                        } else if (rawDeltaY < 0) {
                                            mEdgeGlowBottom.onPull((float) overscroll / getHeight(),
                                                    1.f - (float) x / getWidth());
                                            if (!mEdgeGlowTop.isFinished()) {
                                                mEdgeGlowTop.onRelease();
                                            }
                                        }
                                    }
                                }
                                mMotionY = y;
                                invalidate();
                            }
                            mLastY = y;
                        }
                        break;

                    case TOUCH_MODE_OVERSCROLL:
                        if (y != mLastY) {
                            final int rawDeltaY = deltaY;
                            deltaY -= mMotionCorrection;
                            int incrementalDeltaY = mLastY != Integer.MIN_VALUE ? y - mLastY : deltaY;

                            final int oldScroll = getScrollY();
                            final int newScroll = oldScroll - incrementalDeltaY;
                            int newDirection = y > mLastY ? 1 : -1;

                            if (mDirection == 0) {
                                mDirection = newDirection;
                            }

                            if (mDirection != newDirection) {
                                // Coming back to 'real' list scrolling
                                incrementalDeltaY = -newScroll;
                                setScrollY(0);

                                // No need to do all this work if we're not going to move anyway
                                if (incrementalDeltaY != 0) {
                                    trackMotionScroll(incrementalDeltaY, incrementalDeltaY);
                                }

                                // Check to see if we are back in
                                View motionView = this.getChildAt(mMotionPosition - mFirstPosition);
                                if (motionView != null) {
                                    mTouchMode = TOUCH_MODE_SCROLL;

                                    // We did not scroll the full amount. Treat this essentially like the
                                    // start of a new touch scroll
                                    final int motionPosition = findClosestMotionRow(y);

                                    mMotionCorrection = 0;
                                    motionView = getChildAt(motionPosition - mFirstPosition);
                                    mMotionViewOriginalTop = motionView.getTop();
                                    mMotionY = y;
                                    mMotionPosition = motionPosition;
                                }
                            } else {
                                overScrollBy(0, -incrementalDeltaY, 0, getScrollY(), 0, 0,
                                        0, mOverscrollDistance, true);
                                final int overscrollMode = getOverScrollMode();
                                if (overscrollMode == OVER_SCROLL_ALWAYS ||
                                        (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS &&
                                                !contentFits())) {
                                    if (rawDeltaY > 0) {
                                        mEdgeGlowTop.onPull((float) -incrementalDeltaY / getHeight(),
                                                (float) x / getWidth());
                                        if (!mEdgeGlowBottom.isFinished()) {
                                            mEdgeGlowBottom.onRelease();
                                        }
                                    } else if (rawDeltaY < 0) {
                                        mEdgeGlowBottom.onPull((float) -incrementalDeltaY / getHeight(),
                                                1.f - (float) x / getWidth());
                                        if (!mEdgeGlowTop.isFinished()) {
                                            mEdgeGlowTop.onRelease();
                                        }
                                    }
                                    invalidate();
                                }
                                if (Math.abs(mOverscrollDistance) == Math.abs(getScrollY())) {
                                    // Don't allow overfling if we're at the edge.
                                    mVelocityTracker.clear();
                                }
                            }
                            mLastY = y;
                            mDirection = newDirection;
                        }
                        break;
                }

                break;
            }

            case MotionEvent.ACTION_UP: {
                switch (mTouchMode) {
                    case TOUCH_MODE_DOWN:
                    case TOUCH_MODE_TAP:
                    case TOUCH_MODE_DONE_WAITING:
                        final int motionPosition = mMotionPosition;
                        final View child = getChildAt(motionPosition - mFirstPosition);
                        if (child != null && !child.hasFocusable()) {
                            if (mTouchMode != TOUCH_MODE_DOWN) {
                                child.setPressed(false);
                            }

                            if (mPerformClick == null) {
                                mPerformClick = new PerformClick();
                            }

                            final AbsListView.PerformClick performClick = mPerformClick;
                            performClick.mChild = child;
                            performClick.mClickMotionPosition = motionPosition;
                            performClick.rememberWindowAttachCount();

                            mResurrectToPosition = motionPosition;

                            if (mTouchMode == TOUCH_MODE_DOWN || mTouchMode == TOUCH_MODE_TAP) {
                                final Handler handler = getHandler();
                                if (handler != null) {
                                    handler.removeCallbacks(mTouchMode == TOUCH_MODE_DOWN ?
                                            mPendingCheckForTap : mPendingCheckForLongPress);
                                }
                                mLayoutMode = LAYOUT_NORMAL;
                                if (!mDataChanged && mAdapter.isEnabled(motionPosition)) {
                                    mTouchMode = TOUCH_MODE_TAP;
                                    setSelectedPositionInt(mMotionPosition);
                                    layoutChildren();
                                    child.setPressed(true);
                                    positionSelector(mMotionPosition, child);
                                    setPressed(true);
                                    if (mSelector != null) {
                                        Drawable d = mSelector.getCurrent();
                                        if (d != null && d instanceof TransitionDrawable) {
                                            ((TransitionDrawable) d).resetTransition();
                                        }
                                        DrawableCompat.setHotspot(mSelector, ev.getX(), ev.getY());
                                    }
                                    postDelayed(new Runnable() {
                                        public void run() {
                                            child.setPressed(false);
                                            setPressed(false);
                                            if (!mDataChanged) {
                                                post(performClick);
                                            }
                                            mTouchMode = TOUCH_MODE_REST;
                                        }
                                    }, ViewConfiguration.getPressedStateDuration());
                                } else {
                                    mTouchMode = TOUCH_MODE_REST;
                                    updateSelectorState();
                                }
                                return true;
                            } else if (!mDataChanged && mAdapter.isEnabled(motionPosition)) {
                                post(performClick);
                            }
                        }
                        mTouchMode = TOUCH_MODE_REST;
                        updateSelectorState();
                        break;
                    case TOUCH_MODE_SCROLL:
                        final int childCount = getChildCount();
                        if (childCount > 0) {
                            final int firstChildTop = getChildAt(0).getTop();
                            final int lastChildBottom = getChildAt(childCount - 1).getBottom();
                            final int contentTop = mListPadding.top;
                            final int contentBottom = getHeight() - mListPadding.bottom;
                            if (mFirstPosition == 0 && firstChildTop >= contentTop &&
                                    mFirstPosition + childCount < mItemCount &&
                                    lastChildBottom <= getHeight() - contentBottom) {
                                mTouchMode = TOUCH_MODE_REST;
                                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                            } else {
                                final VelocityTracker velocityTracker = mVelocityTracker;
                                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                                final int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);

                                // Fling if we have enough velocity and we aren't at a boundary.
                                // Since we can potentially overfling more than we can overscroll, don't
                                // allow the weird behavior where you can scroll to a boundary then
                                // fling further.
                                if (Math.abs(initialVelocity) > mMinimumVelocity &&
                                        !((mFirstPosition == 0 &&
                                                firstChildTop == contentTop - mOverscrollDistance) ||
                                                (mFirstPosition + childCount == mItemCount &&
                                                        lastChildBottom == contentBottom + mOverscrollDistance))) {
                                    if (mFlingRunnable == null) {
                                        mFlingRunnable = new FlingRunnable();
                                    }
                                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);

                                    mFlingRunnable.start(-initialVelocity);
                                } else {
                                    mTouchMode = TOUCH_MODE_REST;
                                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                                }
                            }
                        } else {
                            mTouchMode = TOUCH_MODE_REST;
                            reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                        }
                        break;

                    case TOUCH_MODE_OVERSCROLL:
                        if (mFlingRunnable == null) {
                            mFlingRunnable = new FlingRunnable();
                        }
                        final VelocityTracker velocityTracker = mVelocityTracker;
                        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                        final int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);

                        reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                        if (Math.abs(initialVelocity) > mMinimumVelocity) {
                            mFlingRunnable.startOverfling(-initialVelocity);
                        } else {
                            mFlingRunnable.startSpringback();
                        }

                        break;
                }

                setPressed(false);

                if (mEdgeGlowTop != null) {
                    mEdgeGlowTop.onRelease();
                    mEdgeGlowBottom.onRelease();
                }

                // Need to redraw since we probably aren't drawing the selector anymore
                invalidate();

                final Handler handler = getHandler();
                if (handler != null) {
                    handler.removeCallbacks(mPendingCheckForLongPress);
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                mActivePointerId = INVALID_POINTER;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                switch (mTouchMode) {
                    case TOUCH_MODE_OVERSCROLL:
                        if (mFlingRunnable == null) {
                            mFlingRunnable = new FlingRunnable();
                        }
                        mFlingRunnable.startSpringback();
                        break;

                    case TOUCH_MODE_OVERFLING:
                        // Do nothing - let it play out.
                        break;

                    default:
                        mTouchMode = TOUCH_MODE_REST;
                        setPressed(false);
                        View motionView = this.getChildAt(mMotionPosition - mFirstPosition);
                        if (motionView != null) {
                            motionView.setPressed(false);
                        }
                        clearScrollingCache();

                        final Handler handler = getHandler();
                        if (handler != null) {
                            handler.removeCallbacks(mPendingCheckForLongPress);
                        }

                        if (mVelocityTracker != null) {
                            mVelocityTracker.recycle();
                            mVelocityTracker = null;
                        }
                }

                if (mEdgeGlowTop != null) {
                    mEdgeGlowTop.onRelease();
                    mEdgeGlowBottom.onRelease();
                }
                mActivePointerId = INVALID_POINTER;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                final int x = mMotionX;
                final int y = mMotionY;
                final int motionPosition = pointToPosition(x, y);
                if (motionPosition >= 0) {
                    // Remember where the motion event started
                    v = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalTop = v.getTop();
                    mMotionPosition = motionPosition;
                }
                mLastY = y;
                break;
            }
        }

        return true;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
        setScrollY(scrollY);

        if (clampedY) {
            // Velocity is broken by hitting the limit; don't start a fling off of this.
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
        awakenScrollBars();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowTop != null) {
            final int scrollY = getScrollY();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();

                canvas.translate(-width / 2, Math.min(0, scrollY + mFirstPositionDistanceGuess));
                mEdgeGlowTop.setSize(width * 2, getHeight());
                if (mEdgeGlowTop.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight();

                canvas.translate(-width / 2,
                        Math.max(height, scrollY + mLastPositionDistanceGuess));
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width * 2, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL:
                    if (mTouchMode == TOUCH_MODE_REST) {
                        final float vscroll
                                = MotionEventCompat.getAxisValue(event, MotionEventCompat.AXIS_VSCROLL);
                        if (vscroll != 0) {
                            final int delta = (int) (vscroll * ViewUtils.getVerticalScrollFactor(this));
                            if (!trackMotionScroll(delta, delta)) {
                                return true;
                            }
                        }
                    }
                    break;
            }
        }

        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        View v;

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                int touchMode = mTouchMode;
                if (touchMode == TOUCH_MODE_OVERFLING || touchMode == TOUCH_MODE_OVERSCROLL) {
                    mMotionCorrection = 0;
                    return true;
                }

                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);

                int motionPosition = findMotionRow(y);
                if (touchMode != TOUCH_MODE_FLING && motionPosition >= 0) {
                    // User clicked on an actual view (and was not stopping a fling).
                    // Remember where the motion event started
                    v = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalTop = v.getTop();
                    mMotionX = x;
                    mMotionY = y;
                    mMotionPosition = motionPosition;
                    mTouchMode = TOUCH_MODE_DOWN;
                    clearScrollingCache();
                }
                mLastY = Integer.MIN_VALUE;
                if (touchMode == TOUCH_MODE_FLING) {
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                switch (mTouchMode) {
                    case TOUCH_MODE_DOWN:
                        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        final int y = (int) ev.getY(pointerIndex);
                        if (startScrollIfNeeded(y - mMotionY)) {
                            return true;
                        }
                        break;
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                mTouchMode = TOUCH_MODE_REST;
                mActivePointerId = INVALID_POINTER;
                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                break;
            }
        }

        return false;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mMotionX = (int) ev.getX(newPointerIndex);
            mMotionY = (int) ev.getY(newPointerIndex);
            mMotionCorrection = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTouchables(ArrayList<View> views) {
        final int count = getChildCount();
        final int firstPosition = mFirstPosition;
        final ListAdapter adapter = mAdapter;

        if (adapter == null) {
            return;
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (adapter.isEnabled(firstPosition + i)) {
                views.add(child);
            }
            child.addTouchables(views);
        }
    }

    void reportScrollStateChange(int newState) {
        if (newState != mLastScrollState) {
            if (mOnScrollListener != null) {
                mOnScrollListener.onScrollStateChanged(this, newState);
                mLastScrollState = newState;
            }
        }
    }

    private class FlingRunnable implements Runnable {
        /**
         * Tracks the decay of a fling scroll
         */
        private final ScrollerCompat mScroller;

        /**
         * Y value reported by mScroller on the previous fling
         */
        private int mLastFlingY;

        FlingRunnable() {
            mScroller = ScrollerCompat.create(getContext());
        }

        void start(int initialVelocity) {
            int initialY = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingY = initialY;
            mScroller.fling(0, initialY, 0, initialVelocity,
                    0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            mTouchMode = TOUCH_MODE_FLING;
            post(this);
        }

        void startSpringback() {
            if (mScroller.springBack(0, getScrollY(), 0, 0, 0, 0)) {
                mTouchMode = TOUCH_MODE_OVERFLING;
                invalidate();
                post(this);
            } else {
                mTouchMode = TOUCH_MODE_REST;
            }
        }

        void startOverfling(int initialVelocity) {
            final int min = getScrollY() > 0 ? Integer.MIN_VALUE : 0;
            final int max = getScrollY() > 0 ? 0 : Integer.MAX_VALUE;
            mScroller.fling(0, getScrollY(), 0, initialVelocity, 0, 0, min, max, 0, getHeight());
            mTouchMode = TOUCH_MODE_OVERFLING;
            invalidate();
            post(this);
        }

        void edgeReached(int delta) {
            mScroller.notifyVerticalEdgeReached(getScrollY(), 0, 0);
            final int overscrollMode = getOverScrollMode();
            if (overscrollMode == OVER_SCROLL_ALWAYS ||
                    (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && !contentFits())) {
                mTouchMode = TOUCH_MODE_OVERFLING;
                final int vel = (int) mScroller.getCurrVelocity();
                if (delta > 0) {
                    mEdgeGlowTop.onAbsorb(vel);
                } else {
                    mEdgeGlowBottom.onAbsorb(vel);
                }
            }
            invalidate();
            post(this);
        }


        void startScroll(int distance, int duration) {
            int initialY = distance < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingY = initialY;
            mScroller.startScroll(0, initialY, 0, distance, duration);
            mTouchMode = TOUCH_MODE_FLING;
            post(this);
        }

        private void endFling() {
            mTouchMode = TOUCH_MODE_REST;

            reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
            clearScrollingCache();

            removeCallbacks(this);

            if (mPositionScroller != null) {
                removeCallbacks(mPositionScroller);
            }
        }

        public void run() {
            switch (mTouchMode) {
                default:
                    return;

                case TOUCH_MODE_FLING: {
                    if (mItemCount == 0 || getChildCount() == 0) {
                        endFling();
                        return;
                    }

                    final ScrollerCompat scroller = mScroller;
                    boolean more = scroller.computeScrollOffset();
                    final int y = scroller.getCurrY();

                    // Flip sign to convert finger direction to list items direction
                    // (e.g. finger moving down means list is moving towards the top)
                    int delta = mLastFlingY - y;

                    // Pretend that each frame of a fling scroll is a touch scroll
                    if (delta > 0) {
                        // List is moving towards the top. Use first view as mMotionPosition
                        mMotionPosition = mFirstPosition;
                        final View firstView = getChildAt(0);
                        mMotionViewOriginalTop = firstView.getTop();

                        // Don't fling more than 1 screen
                        delta = Math.min(getHeight() - getPaddingBottom() - getPaddingTop() - 1, delta);
                    } else {
                        // List is moving towards the bottom. Use last view as mMotionPosition
                        int offsetToLast = getChildCount() - 1;
                        mMotionPosition = mFirstPosition + offsetToLast;

                        final View lastView = getChildAt(offsetToLast);
                        mMotionViewOriginalTop = lastView.getTop();

                        // Don't fling more than 1 screen
                        delta = Math.max(-(getHeight() - getPaddingBottom() - getPaddingTop() - 1), delta);
                    }

                    // Check to see if we have bumped into the scroll limit
                    View motionView = getChildAt(mMotionPosition - mFirstPosition);
                    int oldTop = 0;
                    if (motionView != null) {
                        oldTop = motionView.getTop();
                    }

                    final boolean atEnd = trackMotionScroll(delta, delta);
                    if (atEnd) {
                        if (motionView != null) {
                            // Tweak the scroll for how far we overshot
                            int overshoot = -(delta - (motionView.getTop() - oldTop));
                            overScrollBy(0, overshoot, 0, getScrollY(), 0, 0,
                                    0, 0, false);
                        }
                        if (more) {
                            edgeReached(delta);
                        }
                        break;
                    }

                    if (more) {
                        invalidate();
                        mLastFlingY = y;
                        post(this);
                    } else {
                        endFling();
                    }
                    break;
                }

                case TOUCH_MODE_OVERFLING: {
                    final ScrollerCompat scroller = mScroller;
                    if (scroller.computeScrollOffset()) {
                        final int scrollY = getScrollY();
                        final int deltaY = scroller.getCurrY() - scrollY;
                        if (overScrollBy(0, deltaY, 0, scrollY, 0, 0,
                                0, 0, false)) {
                            startSpringback();
                        } else {
                            invalidate();
                            post(this);
                        }
                    } else {
                        endFling();
                    }
                    break;
                }
            }

        }
    }

    class PositionScroller implements Runnable {
        private static final int SCROLL_DURATION = 400;

        private static final int MOVE_DOWN_POS = 1;
        private static final int MOVE_UP_POS = 2;
        private static final int MOVE_DOWN_BOUND = 3;
        private static final int MOVE_UP_BOUND = 4;

        private int mMode;
        private int mTargetPos;
        private int mBoundPos;
        private int mLastSeenPos;
        private int mScrollDuration;
        private final int mExtraScroll;

        PositionScroller() {
            mExtraScroll = ViewConfiguration.get(getContext()).getScaledFadingEdgeLength();
        }

        void start(int position) {
            final int firstPos = mFirstPosition;
            final int lastPos = firstPos + getChildCount() - 1;

            int viewTravelCount;
            if (position <= firstPos) {
                viewTravelCount = firstPos - position + 1;
                mMode = MOVE_UP_POS;
            } else if (position >= lastPos) {
                viewTravelCount = position - lastPos + 1;
                mMode = MOVE_DOWN_POS;
            } else {
                // Already on screen, nothing to do
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = position;
            mBoundPos = INVALID_POSITION;
            mLastSeenPos = INVALID_POSITION;

            post(this);
        }

        void start(int position, int boundPosition) {
            if (boundPosition == INVALID_POSITION) {
                start(position);
                return;
            }

            final int firstPos = mFirstPosition;
            final int lastPos = firstPos + getChildCount() - 1;

            int viewTravelCount;
            if (position <= firstPos) {
                final int boundPosFromLast = lastPos - boundPosition;
                if (boundPosFromLast < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = firstPos - position + 1;
                final int boundTravel = boundPosFromLast - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_UP_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_UP_POS;
                }
            } else if (position >= lastPos) {
                final int boundPosFromFirst = boundPosition - firstPos;
                if (boundPosFromFirst < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = position - lastPos + 1;
                final int boundTravel = boundPosFromFirst - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_DOWN_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_DOWN_POS;
                }
            } else {
                // Already on screen, nothing to do
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = position;
            mBoundPos = boundPosition;
            mLastSeenPos = INVALID_POSITION;

            post(this);
        }

        void stop() {
            removeCallbacks(this);
        }

        public void run() {
            final int listHeight = getHeight();
            final int firstPos = mFirstPosition;

            switch (mMode) {
                case MOVE_DOWN_POS: {
                    final int lastViewIndex = getChildCount() - 1;
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastViewIndex < 0) {
                        return;
                    }

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewHeight = lastView.getHeight();
                    final int lastViewTop = lastView.getTop();
                    final int lastViewPixelsShowing = listHeight - lastViewTop;
                    final int extraScroll = lastPos < mItemCount - 1 ? mExtraScroll : mListPadding.bottom;

                    smoothScrollBy(lastViewHeight - lastViewPixelsShowing + extraScroll,
                            mScrollDuration);

                    mLastSeenPos = lastPos;
                    if (lastPos < mTargetPos) {
                        post(this);
                    }
                    break;
                }

                case MOVE_DOWN_BOUND: {
                    final int nextViewIndex = 1;
                    final int childCount = getChildCount();

                    if (firstPos == mBoundPos || childCount <= nextViewIndex
                            || firstPos + childCount >= mItemCount) {
                        return;
                    }
                    final int nextPos = firstPos + nextViewIndex;

                    if (nextPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View nextView = getChildAt(nextViewIndex);
                    final int nextViewHeight = nextView.getHeight();
                    final int nextViewTop = nextView.getTop();
                    final int extraScroll = mExtraScroll;
                    if (nextPos < mBoundPos) {
                        smoothScrollBy(Math.max(0, nextViewHeight + nextViewTop - extraScroll),
                                mScrollDuration);

                        mLastSeenPos = nextPos;

                        post(this);
                    } else {
                        if (nextViewTop > extraScroll) {
                            smoothScrollBy(nextViewTop - extraScroll, mScrollDuration);
                        }
                    }
                    break;
                }

                case MOVE_UP_POS: {
                    if (firstPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View firstView = getChildAt(0);
                    if (firstView == null) {
                        return;
                    }
                    final int firstViewTop = firstView.getTop();
                    final int extraScroll = firstPos > 0 ? mExtraScroll : mListPadding.top;

                    smoothScrollBy(firstViewTop - extraScroll, mScrollDuration);

                    mLastSeenPos = firstPos;

                    if (firstPos > mTargetPos) {
                        post(this);
                    }
                    break;
                }

                case MOVE_UP_BOUND: {
                    final int lastViewIndex = getChildCount() - 2;
                    if (lastViewIndex < 0) {
                        return;
                    }
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewHeight = lastView.getHeight();
                    final int lastViewTop = lastView.getTop();
                    final int lastViewPixelsShowing = listHeight - lastViewTop;
                    mLastSeenPos = lastPos;
                    if (lastPos > mBoundPos) {
                        smoothScrollBy(-(lastViewPixelsShowing - mExtraScroll), mScrollDuration);
                        post(this);
                    } else {
                        final int bottom = listHeight - mExtraScroll;
                        final int lastViewBottom = lastViewTop + lastViewHeight;
                        if (bottom > lastViewBottom) {
                            smoothScrollBy(-(bottom - lastViewBottom), mScrollDuration);
                        }
                    }
                    break;
                }

                default:
                    break;
            }
        }
    }

    public void smoothScrollToPosition(int position) {
        if (mPositionScroller == null) {
            mPositionScroller = new PositionScroller();
        }
        mPositionScroller.start(position);
    }

    public void smoothScrollToPosition(int position, int boundPosition) {
        if (mPositionScroller == null) {
            mPositionScroller = new PositionScroller();
        }
        mPositionScroller.start(position, boundPosition);
    }

    public void smoothScrollBy(int distance, int duration) {
        if (mFlingRunnable == null) {
            mFlingRunnable = new FlingRunnable();
        } else {
            mFlingRunnable.endFling();
        }
        mFlingRunnable.startScroll(distance, duration);
    }

    private void createScrollingCache() {
        if (mScrollingCacheEnabled && !mCachingStarted) {
            setChildrenDrawnWithCacheEnabled(true);
            setChildrenDrawingCacheEnabled(true);
            mCachingStarted = true;
        }
    }

    private void clearScrollingCache() {
        if (mClearScrollingCache == null) {
            mClearScrollingCache = new Runnable() {
                public void run() {
                    if (mCachingStarted) {
                        mCachingStarted = false;
                        setChildrenDrawnWithCacheEnabled(false);
                        if ((getPersistentDrawingCache() & PERSISTENT_SCROLLING_CACHE) == 0) {
                            setChildrenDrawingCacheEnabled(false);
                        }
                        if (!isAlwaysDrawnWithCacheEnabled()) {
                            invalidate();
                        }
                    }
                }
            };
        }
        post(mClearScrollingCache);
    }

    boolean trackMotionScroll(int deltaY, int incrementalDeltaY) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return true;
        }

        final int firstTop = getChildAt(0).getTop();
        final int lastBottom = getChildAt(childCount - 1).getBottom();

        final Rect listPadding = mListPadding;

        final int spaceAbove = listPadding.top - firstTop;
        final int end = getHeight() - listPadding.bottom;
        final int spaceBelow = lastBottom - end;

        final int height = getHeight() - getPaddingBottom() - getPaddingTop();
        if (deltaY < 0) {
            deltaY = Math.max(-(height - 1), deltaY);
        } else {
            deltaY = Math.min(height - 1, deltaY);
        }

        if (incrementalDeltaY < 0) {
            incrementalDeltaY = Math.max(-(height - 1), incrementalDeltaY);
        } else {
            incrementalDeltaY = Math.min(height - 1, incrementalDeltaY);
        }

        final int firstPosition = mFirstPosition;

        // Update our guesses for where the first and last views are
        if (firstPosition == 0) {
            mFirstPositionDistanceGuess = firstTop - mListPadding.top;
        } else {
            mFirstPositionDistanceGuess += incrementalDeltaY;
        }
        if (firstPosition + childCount == mItemCount) {
            mLastPositionDistanceGuess = lastBottom + mListPadding.bottom;
        } else {
            mLastPositionDistanceGuess += incrementalDeltaY;
        }


        if (firstPosition == 0 && firstTop >= listPadding.top && incrementalDeltaY >= 0) {
            // Don't need to move views down if the top of the first position
            // is already visible
            return incrementalDeltaY != 0;
        }

        if (firstPosition + childCount == mItemCount && lastBottom <= end &&
                incrementalDeltaY <= 0) {
            // Don't need to move views up if the bottom of the last position
            // is already visible
            return incrementalDeltaY != 0;
        }

        final boolean down = incrementalDeltaY < 0;

        final boolean inTouchMode = isInTouchMode();
        if (inTouchMode) {
            hideSelector();
        }

        final int headerViewsCount = getHeaderViewsCount();
        final int footerViewsStart = mItemCount - getFooterViewsCount();

        int start = 0;
        int count = 0;

        if (down) {
            final int top = listPadding.top - incrementalDeltaY;
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                if (child.getBottom() >= top) {
                    break;
                } else {
                    count++;
                    int position = firstPosition + i;
                    if (position >= headerViewsCount && position < footerViewsStart) {
                        mRecycler.addScrapView(child);
                    }
                }
            }
        } else {
            final int bottom = getHeight() - listPadding.bottom - incrementalDeltaY;
            for (int i = childCount - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                if (child.getTop() <= bottom) {
                    break;
                } else {
                    start = i;
                    count++;
                    int position = firstPosition + i;
                    if (position >= headerViewsCount && position < footerViewsStart) {
                        mRecycler.addScrapView(child);
                    }
                }
            }
        }

        mMotionViewNewTop = mMotionViewOriginalTop + deltaY;

        mBlockLayoutRequests = true;

        if (count > 0) {
            detachViewsFromParent(start, count);
        }
        ViewUtils.offsetChildrenTopAndBottom(this, incrementalDeltaY);

        if (down) {
            mFirstPosition += count;
        }

        invalidate();

        final int absIncrementalDeltaY = Math.abs(incrementalDeltaY);
        if (spaceAbove < absIncrementalDeltaY || spaceBelow < absIncrementalDeltaY) {
            fillGap(down);
        }

        if (!inTouchMode && mSelectedPosition != INVALID_POSITION) {
            final int childIndex = mSelectedPosition - mFirstPosition;
            if (childIndex >= 0 && childIndex < getChildCount()) {
                positionSelector(mSelectedPosition, getChildAt(childIndex));
            }
        } else if (mSelectorPosition != INVALID_POSITION) {
            final int childIndex = mSelectorPosition - mFirstPosition;
            if (childIndex >= 0 && childIndex < getChildCount()) {
                positionSelector(INVALID_POSITION, getChildAt(childIndex));
            }
        } else {
            mSelectorRect.setEmpty();
        }

        mBlockLayoutRequests = false;

        invokeOnItemScrollListener();
        awakenScrollBars();

        return false;
    }

    int getHeaderViewsCount() {
        return 0;
    }

    int getFooterViewsCount() {
        return 0;
    }

    abstract void fillGap(boolean down);

    void hideSelector() {
        if (mSelectedPosition != INVALID_POSITION) {
            if (mLayoutMode != LAYOUT_SPECIFIC) {
                mResurrectToPosition = mSelectedPosition;
            }
            if (mNextSelectedPosition >= 0 && mNextSelectedPosition != mSelectedPosition) {
                mResurrectToPosition = mNextSelectedPosition;
            }
            setSelectedPositionInt(INVALID_POSITION);
            setNextSelectedPositionInt(INVALID_POSITION);
            mSelectedTop = 0;
            mSelectorRect.setEmpty();
        }
    }

    int reconcileSelectedPosition() {
        int position = mSelectedPosition;
        if (position < 0) {
            position = mResurrectToPosition;
        }
        position = Math.max(0, position);
        position = Math.min(position, mItemCount - 1);
        return position;
    }

    abstract int findMotionRow(int y);

    int findClosestMotionRow(int y) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return INVALID_POSITION;
        }

        final int motionRow = findMotionRow(y);
        return motionRow != INVALID_POSITION ? motionRow : mFirstPosition + childCount - 1;
    }

    public void invalidateViews() {
        mDataChanged = true;
        rememberSyncState();
        requestLayout();
        invalidate();
    }

    abstract void setSelectionInt(int position);

    boolean resurrectSelection() {
        final int childCount = getChildCount();

        if (childCount <= 0) {
            return false;
        }

        int selectedTop = 0;
        int selectedPos;
        int childrenTop = mListPadding.top;
        int childrenBottom = getBottom() - getTop() - mListPadding.bottom;
        final int firstPosition = mFirstPosition;
        final int toPosition = mResurrectToPosition;
        boolean down = true;

        if (toPosition >= firstPosition && toPosition < firstPosition + childCount) {
            selectedPos = toPosition;

            final View selected = getChildAt(selectedPos - mFirstPosition);
            selectedTop = selected.getTop();
            int selectedBottom = selected.getBottom();

            // We are scrolled, don't get in the fade
            if (selectedTop < childrenTop) {
                selectedTop = childrenTop + getVerticalFadingEdgeLength();
            } else if (selectedBottom > childrenBottom) {
                selectedTop = childrenBottom - selected.getMeasuredHeight()
                        - getVerticalFadingEdgeLength();
            }
        } else {
            if (toPosition < firstPosition) {
                // Default to selecting whatever is first
                selectedPos = firstPosition;
                for (int i = 0; i < childCount; i++) {
                    final View v = getChildAt(i);
                    final int top = v.getTop();

                    if (i == 0) {
                        // Remember the position of the first item
                        selectedTop = top;
                        // See if we are scrolled at all
                        if (firstPosition > 0 || top < childrenTop) {
                            // If we are scrolled, don't select anything that is
                            // in the fade region
                            childrenTop += getVerticalFadingEdgeLength();
                        }
                    }
                    if (top >= childrenTop) {
                        // Found a view whose top is fully visisble
                        selectedPos = firstPosition + i;
                        selectedTop = top;
                        break;
                    }
                }
            } else {
                final int itemCount = mItemCount;
                down = false;
                selectedPos = firstPosition + childCount - 1;

                for (int i = childCount - 1; i >= 0; i--) {
                    final View v = getChildAt(i);
                    final int top = v.getTop();
                    final int bottom = v.getBottom();

                    if (i == childCount - 1) {
                        selectedTop = top;
                        if (firstPosition + childCount < itemCount || bottom > childrenBottom) {
                            childrenBottom -= getVerticalFadingEdgeLength();
                        }
                    }

                    if (bottom <= childrenBottom) {
                        selectedPos = firstPosition + i;
                        selectedTop = top;
                        break;
                    }
                }
            }
        }

        mResurrectToPosition = INVALID_POSITION;
        removeCallbacks(mFlingRunnable);
        mTouchMode = TOUCH_MODE_REST;
        clearScrollingCache();
        mSpecificTop = selectedTop;
        selectedPos = lookForSelectablePosition(selectedPos, down);
        if (selectedPos >= firstPosition && selectedPos <= getLastVisiblePosition()) {
            mLayoutMode = LAYOUT_SPECIFIC;
            updateSelectorState();
            setSelectionInt(selectedPos);
            invokeOnItemScrollListener();
        } else {
            selectedPos = INVALID_POSITION;
        }
        reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);

        return selectedPos >= 0;
    }

    @Override
    protected void handleDataChanged() {
        int count = mItemCount;
        if (count > 0) {

            int newPos;

            int selectablePos;

            // Find the row we are supposed to sync to
            if (mNeedSync) {
                // Update this first, since setNextSelectedPositionInt inspects it
                mNeedSync = false;

                if (mTranscriptMode == TRANSCRIPT_MODE_ALWAYS_SCROLL ||
                        (mTranscriptMode == TRANSCRIPT_MODE_NORMAL &&
                                mFirstPosition + getChildCount() >= mOldItemCount)) {
                    mLayoutMode = LAYOUT_FORCE_BOTTOM;
                    return;
                }

                switch (mSyncMode) {
                    case SYNC_SELECTED_POSITION:
                        if (isInTouchMode()) {
                            // We saved our state when not in touch mode. (We know this because
                            // mSyncMode is SYNC_SELECTED_POSITION.) Now we are trying to
                            // restore in touch mode. Just leave mSyncPosition as it is (possibly
                            // adjusting if the available range changed) and return.
                            mLayoutMode = LAYOUT_SYNC;
                            mSyncPosition = Math.min(Math.max(0, mSyncPosition), count - 1);

                            return;
                        } else {
                            // See if we can find a position in the new data with the same
                            // id as the old selection. This will change mSyncPosition.
                            newPos = findSyncPosition();
                            if (newPos >= 0) {
                                // Found it. Now verify that new selection is still selectable
                                selectablePos = lookForSelectablePosition(newPos, true);
                                if (selectablePos == newPos) {
                                    // Same row id is selected
                                    mSyncPosition = newPos;

                                    if (mSyncHeight == getHeight()) {
                                        // If we are at the same height as when we saved state, try
                                        // to restore the scroll position too.
                                        mLayoutMode = LAYOUT_SYNC;
                                    } else {
                                        // We are not the same height as when the selection was saved, so
                                        // don't try to restore the exact position
                                        mLayoutMode = LAYOUT_SET_SELECTION;
                                    }

                                    // Restore selection
                                    setNextSelectedPositionInt(newPos);
                                    return;
                                }
                            }
                        }
                        break;
                    case SYNC_FIRST_POSITION:
                        // Leave mSyncPosition as it is -- just pin to available range
                        mLayoutMode = LAYOUT_SYNC;
                        mSyncPosition = Math.min(Math.max(0, mSyncPosition), count - 1);

                        return;
                }
            }

            if (!isInTouchMode()) {
                // We couldn't find matching data -- try to use the same position
                newPos = getSelectedItemPosition();

                // Pin position to the available range
                if (newPos >= count) {
                    newPos = count - 1;
                }
                if (newPos < 0) {
                    newPos = 0;
                }

                // Make sure we select something selectable -- first look down
                selectablePos = lookForSelectablePosition(newPos, true);

                if (selectablePos >= 0) {
                    setNextSelectedPositionInt(selectablePos);
                    return;
                } else {
                    // Looking down didn't work -- try looking up
                    selectablePos = lookForSelectablePosition(newPos, false);
                    if (selectablePos >= 0) {
                        setNextSelectedPositionInt(selectablePos);
                        return;
                    }
                }
            } else {

                // We already know where we want to resurrect the selection
                if (mResurrectToPosition >= 0) {
                    return;
                }
            }

        }

        // Nothing is selected. Give up and reset everything.
        mLayoutMode = mStackFromBottom ? LAYOUT_FORCE_BOTTOM : LAYOUT_FORCE_TOP;
        mSelectedPosition = INVALID_POSITION;
        mSelectedRowId = INVALID_ROW_ID;
        mNextSelectedPosition = INVALID_POSITION;
        mNextSelectedRowId = INVALID_ROW_ID;
        mNeedSync = false;
        mSelectorPosition = INVALID_POSITION;
        checkSelectionChanged();
    }

    static int getDistance(Rect source, Rect dest, int direction) {
        int sX, sY; // source x, y
        int dX, dY; // dest x, y
        switch (direction) {
            case View.FOCUS_RIGHT:
                sX = source.right;
                sY = source.top + source.height() / 2;
                dX = dest.left;
                dY = dest.top + dest.height() / 2;
                break;
            case View.FOCUS_DOWN:
                sX = source.left + source.width() / 2;
                sY = source.bottom;
                dX = dest.left + dest.width() / 2;
                dY = dest.top;
                break;
            case View.FOCUS_LEFT:
                sX = source.left;
                sY = source.top + source.height() / 2;
                dX = dest.right;
                dY = dest.top + dest.height() / 2;
                break;
            case View.FOCUS_UP:
                sX = source.left + source.width() / 2;
                sY = source.top;
                dX = dest.left + dest.width() / 2;
                dY = dest.bottom;
                break;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
        int deltaX = dX - sX;
        int deltaY = dY - sY;
        return deltaY * deltaY + deltaX * deltaX;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new AbsListView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof AbsListView.LayoutParams;
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        int viewType;
        boolean recycledHeaderFooter;
        boolean forceAdd;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int viewType) {
            super(w, h);
            this.viewType = viewType;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    public void setTranscriptMode(int mode) {
        mTranscriptMode = mode;
    }

    public int getTranscriptMode() {
        return mTranscriptMode;
    }

    @Override
    public int getSolidColor() {
        return mCacheColorHint;
    }

    public void setCacheColorHint(int color) {
        if (color != mCacheColorHint) {
            mCacheColorHint = color;
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                getChildAt(i).setDrawingCacheBackgroundColor(color);
            }
            mRecycler.setCacheColorHint(color);
        }
    }

    public int getCacheColorHint() {
        return mCacheColorHint;
    }

    public void reclaimViews(List<View> views) {
        int childCount = getChildCount();
        RecyclerListener listener = mRecycler.mRecyclerListener;

        // Reclaim views on screen
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            AbsListView.LayoutParams lp = (AbsListView.LayoutParams) child.getLayoutParams();
            // Don't reclaim header or footer views, or views that should be ignored
            if (lp != null && mRecycler.shouldRecycleViewType(lp.viewType)) {
                views.add(child);
                if (listener != null) {
                    // Pretend they went through the scrap heap
                    listener.onMovedToScrapHeap(child);
                }
            }
        }
        mRecycler.reclaimScrapViews(views);
        removeAllViewsInLayout();
    }

    private boolean checkScrap(ArrayList<View> scrap) {
        if (scrap == null) return true;
        boolean result = true;

        final int count = scrap.size();
        for (int i = 0; i < count; i++) {
            final View view = scrap.get(i);
            if (view.getParent() != null || indexOfChild(view) >= 0) {
                result = false;
            }
        }

        return result;
    }

    private void finishGlows() {
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.finish();
            mEdgeGlowBottom.finish();
        }
    }

    public void setRecyclerListener(RecyclerListener listener) {
        mRecycler.mRecyclerListener = listener;
    }

    public interface RecyclerListener {
        void onMovedToScrapHeap(View view);
    }

    class RecycleBin {
        private RecyclerListener mRecyclerListener;

        /**
         * The position of the first view stored in mActiveViews.
         */
        private int mFirstActivePosition;

        /**
         * Views that were on screen at the start of layout. This array is populated at the start of
         * layout, and at the end of layout all view in mActiveViews are moved to mScrapViews.
         * Views in mActiveViews represent a contiguous range of Views, with position of the first
         * view store in mFirstActivePosition.
         */
        private View[] mActiveViews = new View[0];

        /**
         * Unsorted views that can be used by the adapter as a convert view.
         */
        private ArrayList<View>[] mScrapViews;

        private int mViewTypeCount;

        private ArrayList<View> mCurrentScrap;

        public void setViewTypeCount(int viewTypeCount) {
            if (viewTypeCount < 1) {
                throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
            }
            //noinspection unchecked
            ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
            for (int i = 0; i < viewTypeCount; i++) {
                scrapViews[i] = new ArrayList<>();
            }
            mViewTypeCount = viewTypeCount;
            mCurrentScrap = scrapViews[0];
            mScrapViews = scrapViews;
        }

        public void markChildrenDirty() {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                final int scrapCount = scrap.size();
                for (int i = 0; i < scrapCount; i++) {
                    scrap.get(i).forceLayout();
                }
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    final int scrapCount = scrap.size();
                    for (int j = 0; j < scrapCount; j++) {
                        scrap.get(j).forceLayout();
                    }
                }
            }
        }

        public boolean shouldRecycleViewType(int viewType) {
            return viewType >= 0;
        }

        /**
         * Clears the scrap heap.
         */
        void clear() {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                final int scrapCount = scrap.size();
                for (int i = 0; i < scrapCount; i++) {
                    removeDetachedView(scrap.remove(scrapCount - 1 - i), false);
                }
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    final int scrapCount = scrap.size();
                    for (int j = 0; j < scrapCount; j++) {
                        removeDetachedView(scrap.remove(scrapCount - 1 - j), false);
                    }
                }
            }
        }

        /**
         * Fill ActiveViews with all of the children of the AbsListView.
         *
         * @param childCount          The minimum number of views mActiveViews should hold
         * @param firstActivePosition The position of the first view that will be stored in
         *                            mActiveViews
         */
        void fillActiveViews(int childCount, int firstActivePosition) {
            if (mActiveViews.length < childCount) {
                mActiveViews = new View[childCount];
            }
            mFirstActivePosition = firstActivePosition;

            final View[] activeViews = mActiveViews;
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                AbsListView.LayoutParams lp = (AbsListView.LayoutParams) child.getLayoutParams();
                // Don't put header or footer views into the scrap heap
                if (lp != null && lp.viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                    // Note:  We do place AdapterView.ITEM_VIEW_TYPE_IGNORE in active views.
                    //        However, we will NOT place them into scrap views.
                    activeViews[i] = child;
                }
            }
        }

        /**
         * Get the view corresponding to the specified position. The view will be removed from
         * mActiveViews if it is found.
         *
         * @param position The position to look up in mActiveViews
         * @return The view if it is found, null otherwise
         */
        View getActiveView(int position) {
            int index = position - mFirstActivePosition;
            final View[] activeViews = mActiveViews;
            if (index >= 0 && index < activeViews.length) {
                final View match = activeViews[index];
                activeViews[index] = null;
                return match;
            }
            return null;
        }

        /**
         * @return A view from the ScrapViews collection. These are unordered.
         */
        View getScrapView(int position) {
            ArrayList<View> scrapViews;
            if (mViewTypeCount == 1) {
                scrapViews = mCurrentScrap;
                int size = scrapViews.size();
                if (size > 0) {
                    return scrapViews.remove(size - 1);
                } else {
                    return null;
                }
            } else {
                int whichScrap = mAdapter.getItemViewType(position);
                if (whichScrap >= 0 && whichScrap < mScrapViews.length) {
                    scrapViews = mScrapViews[whichScrap];
                    int size = scrapViews.size();
                    if (size > 0) {
                        return scrapViews.remove(size - 1);
                    }
                }
            }
            return null;
        }

        /**
         * Put a view into the ScapViews list. These views are unordered.
         *
         * @param scrap The view to add
         */
        void addScrapView(View scrap) {
            AbsListView.LayoutParams lp = (AbsListView.LayoutParams) scrap.getLayoutParams();
            if (lp == null) {
                return;
            }

            // Don't put header or footer views or views that should be ignored
            // into the scrap heap
            int viewType = lp.viewType;
            if (!shouldRecycleViewType(viewType)) {
                if (viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                    removeDetachedView(scrap, false);
                }
                return;
            }

            if (mViewTypeCount == 1) {
                ViewUtils.dispatchStartTemporaryDetach(scrap);
                mCurrentScrap.add(scrap);
            } else {
                ViewUtils.dispatchStartTemporaryDetach(scrap);
                mScrapViews[viewType].add(scrap);
            }

            if (mRecyclerListener != null) {
                mRecyclerListener.onMovedToScrapHeap(scrap);
            }
        }

        /**
         * Move all views remaining in mActiveViews to mScrapViews.
         */
        void scrapActiveViews() {
            final View[] activeViews = mActiveViews;
            final boolean hasListener = mRecyclerListener != null;
            final boolean multipleScraps = mViewTypeCount > 1;

            ArrayList<View> scrapViews = mCurrentScrap;
            final int count = activeViews.length;
            for (int i = count - 1; i >= 0; i--) {
                final View victim = activeViews[i];
                if (victim != null) {
                    int whichScrap = ((AbsListView.LayoutParams) victim.getLayoutParams()).viewType;

                    activeViews[i] = null;

                    if (!shouldRecycleViewType(whichScrap)) {
                        // Do not move views that should be ignored
                        if (whichScrap != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                            removeDetachedView(victim, false);
                        }
                        continue;
                    }

                    if (multipleScraps) {
                        scrapViews = mScrapViews[whichScrap];
                    }
                    ViewUtils.dispatchStartTemporaryDetach(victim);
                    scrapViews.add(victim);

                    if (hasListener) {
                        mRecyclerListener.onMovedToScrapHeap(victim);
                    }
                }
            }

            pruneScrapViews();
        }

        private void pruneScrapViews() {
            final int maxViews = mActiveViews.length;
            final int viewTypeCount = mViewTypeCount;
            final ArrayList<View>[] scrapViews = mScrapViews;
            for (int i = 0; i < viewTypeCount; ++i) {
                final ArrayList<View> scrapPile = scrapViews[i];
                int size = scrapPile.size();
                final int extras = size - maxViews;
                size--;
                for (int j = 0; j < extras; j++) {
                    removeDetachedView(scrapPile.remove(size--), false);
                }
            }
        }

        void reclaimScrapViews(List<View> views) {
            if (mViewTypeCount == 1) {
                views.addAll(mCurrentScrap);
            } else {
                final int viewTypeCount = mViewTypeCount;
                final ArrayList<View>[] scrapViews = mScrapViews;
                for (int i = 0; i < viewTypeCount; ++i) {
                    final ArrayList<View> scrapPile = scrapViews[i];
                    views.addAll(scrapPile);
                }
            }
        }

        void setCacheColorHint(int color) {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                final int scrapCount = scrap.size();
                for (int i = 0; i < scrapCount; i++) {
                    scrap.get(i).setDrawingCacheBackgroundColor(color);
                }
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    final int scrapCount = scrap.size();
                    for (int j = 0; j < scrapCount; j++) {
                        scrap.get(i).setDrawingCacheBackgroundColor(color);
                    }
                }
            }
            // Just in case this is called during a layout pass
            final View[] activeViews = mActiveViews;
            for (final View victim : activeViews) {
                if (victim != null) {
                    victim.setDrawingCacheBackgroundColor(color);
                }
            }
        }
    }
}
