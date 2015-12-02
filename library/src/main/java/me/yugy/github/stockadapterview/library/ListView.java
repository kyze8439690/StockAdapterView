package me.yugy.github.stockadapterview.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.util.LongSparseArray;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Checkable;
import android.widget.ListAdapter;

import java.util.ArrayList;

public class ListView extends AbsListView {
    static final int NO_POSITION = -1;

    public static final int CHOICE_MODE_NONE = 0;
    public static final int CHOICE_MODE_SINGLE = 1;
    public static final int CHOICE_MODE_MULTIPLE = 2;

    private static final float MAX_SCROLL_FACTOR = 0.33f;

    private static final int MIN_SCROLL_PREVIEW_PIXELS = 2;

    public class FixedViewInfo {
        public View view;
        public Object data;
        public boolean isSelectable;
    }

    private ArrayList<FixedViewInfo> mHeaderViewInfos = new ArrayList<>();
    private ArrayList<FixedViewInfo> mFooterViewInfos = new ArrayList<>();

    Drawable mDivider;
    int mDividerHeight;

    Drawable mOverScrollHeader;
    Drawable mOverScrollFooter;

    private boolean mIsCacheColorOpaque;
    private boolean mClipDivider;

    private boolean mHeaderDividersEnabled;
    private boolean mFooterDividersEnabled;

    private boolean mAreAllItemsSelectable = true;

    private boolean mItemsCanFocus = false;

    private int mChoiceMode = CHOICE_MODE_NONE;

    private SparseBooleanArray mCheckStates;
    private LongSparseArray<Boolean> mCheckedIdStates;

    private final Rect mTempRect = new Rect();
    private Paint mDividerPaint;

    private final ArrowScrollFocusResult mArrowScrollFocusResult = new ArrowScrollFocusResult();

    private FocusSelector mFocusSelector;

    private static final int[] ATTRIBUTES_SET = new int[] {
            android.R.attr.divider,
            android.R.attr.overScrollHeader,
            android.R.attr.overScrollFooter,
            android.R.attr.dividerHeight,
            android.R.attr.choiceMode,
            android.R.attr.headerDividersEnabled,
            android.R.attr.footerDividersEnabled
    };

    public ListView(Context context) {
        this(context, null);
    }

    public ListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    public ListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, ATTRIBUTES_SET);

        final Drawable d = a.getDrawable(0);
        if (d != null) {
            // If a divider is specified use its intrinsic height for divider height
            setDivider(d);
        }

        final Drawable osHeader = a.getDrawable(1);
        if (osHeader != null) {
            setOverscrollHeader(osHeader);
        }

        final Drawable osFooter = a.getDrawable(2);
        if (osFooter != null) {
            setOverscrollFooter(osFooter);
        }

        // Use the height specified, zero being the default
        final int dividerHeight = a.getDimensionPixelSize(3, 0);
        if (dividerHeight != 0) {
            setDividerHeight(dividerHeight);
        }

        setChoiceMode(a.getInt(4, CHOICE_MODE_NONE));

        mHeaderDividersEnabled = a.getBoolean(5, true);
        mFooterDividersEnabled = a.getBoolean(6, true);

        a.recycle();
    }

    public int getMaxScrollAmount() {
        return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
    }

    private void adjustViewsUpOrDown() {
        final int childCount = getChildCount();
        int delta;

        if (childCount > 0) {
            View child;

            if (!mStackFromBottom) {
                // Uh-oh -- we came up short. Slide all views up to make them
                // align with the top
                child = getChildAt(0);
                delta = child.getTop() - mListPadding.top;
                if (mFirstPosition != 0) {
                    // It's OK to have some space above the first item if it is
                    // part of the vertical spacing
                    delta -= mDividerHeight;
                }
                if (delta < 0) {
                    // We only are looking to see if we are too low, not too high
                    delta = 0;
                }
            } else {
                // we are too high, slide all views down to align with bottom
                child = getChildAt(childCount - 1);
                delta = child.getBottom() - (getHeight() - mListPadding.bottom);

                if (mFirstPosition + childCount < mItemCount) {
                    // It's OK to have some space below the last item if it is
                    // part of the vertical spacing
                    delta += mDividerHeight;
                }

                if (delta > 0) {
                    delta = 0;
                }
            }

            if (delta != 0) {
                ViewUtils.offsetChildrenTopAndBottom(this, -delta);
            }
        }
    }

    public void addHeaderView(View v, Object data, boolean isSelectable) {
        if (mAdapter != null) {
            throw new IllegalStateException(
                    "Cannot add header view to list -- setAdapter has already been called.");
        }

        FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
        mHeaderViewInfos.add(info);
    }

    public void addHeaderView(View v) {
        addHeaderView(v, null, true);
    }

    @Override
    public int getHeaderViewsCount() {
        return mHeaderViewInfos.size();
    }

    public boolean removeHeaderView(View v) {
        if (mHeaderViewInfos.size() > 0) {
            boolean result = false;
            if (((HeaderViewListAdapter) mAdapter).removeHeader(v)) {
                mDataSetObserver.onChanged();
                result = true;
            }
            removeFixedViewInfo(v, mHeaderViewInfos);
            return result;
        }
        return false;
    }

    private void removeFixedViewInfo(View v, ArrayList<FixedViewInfo> where) {
        int len = where.size();
        for (int i = 0; i < len; ++i) {
            FixedViewInfo info = where.get(i);
            if (info.view == v) {
                where.remove(i);
                break;
            }
        }
    }

    public void addFooterView(View v, Object data, boolean isSelectable) {
        FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
        mFooterViewInfos.add(info);

        // in the case of re-adding a footer view, or adding one later on,
        // we need to notify the observer
        if (mDataSetObserver != null) {
            mDataSetObserver.onChanged();
        }
    }

    public void addFooterView(View v) {
        addFooterView(v, null, true);
    }

    @Override
    public int getFooterViewsCount() {
        return mFooterViewInfos.size();
    }

    public boolean removeFooterView(View v) {
        if (mFooterViewInfos.size() > 0) {
            boolean result = false;
            if (((HeaderViewListAdapter) mAdapter).removeFooter(v)) {
                mDataSetObserver.onChanged();
                result = true;
            }
            removeFixedViewInfo(v, mFooterViewInfos);
            return result;
        }
        return false;
    }

    @Override
    public ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (null != mAdapter) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }

        resetList();
        mRecycler.clear();

        if (mHeaderViewInfos.size() > 0|| mFooterViewInfos.size() > 0) {
            mAdapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, adapter);
        } else {
            mAdapter = adapter;
        }

        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        if (mAdapter != null) {
            mAreAllItemsSelectable = mAdapter.areAllItemsEnabled();
            mOldItemCount = mItemCount;
            mItemCount = mAdapter.getCount();
            checkFocus();

            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);

            mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());

            int position;
            if (mStackFromBottom) {
                position = lookForSelectablePosition(mItemCount - 1, false);
            } else {
                position = lookForSelectablePosition(0, true);
            }
            setSelectedPositionInt(position);
            setNextSelectedPositionInt(position);

            if (mItemCount == 0) {
                // Nothing selected
                checkSelectionChanged();
            }

            if (mChoiceMode != CHOICE_MODE_NONE &&
                    mAdapter.hasStableIds() &&
                    mCheckedIdStates == null) {
                mCheckedIdStates = new LongSparseArray<>();
            }

        } else {
            mAreAllItemsSelectable = true;
            checkFocus();
            // Nothing selected
            checkSelectionChanged();
        }

        if (mCheckStates != null) {
            mCheckStates.clear();
        }

        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }

        requestLayout();
    }

    @Override
    void resetList() {
        // The parent's resetList() will remove all views from the layout so we need to
        // cleanup the state of our footers and headers
        clearRecycledState(mHeaderViewInfos);
        clearRecycledState(mFooterViewInfos);

        super.resetList();

        mLayoutMode = LAYOUT_NORMAL;
    }

    private void clearRecycledState(ArrayList<FixedViewInfo> infos) {
        if (infos != null) {
            final int count = infos.size();

            for (int i = 0; i < count; i++) {
                final View child = infos.get(i).view;
                final LayoutParams p = (LayoutParams) child.getLayoutParams();
                if (p != null) {
                    p.recycledHeaderFooter = false;
                }
            }
        }
    }

    private boolean showingTopFadingEdge() {
        final int listTop = getScrollY() + mListPadding.top;
        return (mFirstPosition > 0) || (getChildAt(0).getTop() > listTop);
    }

    private boolean showingBottomFadingEdge() {
        final int childCount = getChildCount();
        final int bottomOfBottomChild = getChildAt(childCount - 1).getBottom();
        final int lastVisiblePosition = mFirstPosition + childCount - 1;

        final int listBottom = getScrollY() + getHeight() - mListPadding.bottom;

        return (lastVisiblePosition < mItemCount - 1)
                || (bottomOfBottomChild < listBottom);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {

        int rectTopWithinChild = rect.top;

        // offset so rect is in coordinates of the this view
        rect.offset(child.getLeft(), child.getTop());
        rect.offset(-child.getScrollX(), -child.getScrollY());

        final int height = getHeight();
        int listUnfadedTop = getScrollY();
        int listUnfadedBottom = listUnfadedTop + height;
        final int fadingEdge = getVerticalFadingEdgeLength();

        if (showingTopFadingEdge()) {
            // leave room for top fading edge as long as rect isn't at very top
            if ((mSelectedPosition > 0) || (rectTopWithinChild > fadingEdge)) {
                listUnfadedTop += fadingEdge;
            }
        }

        int childCount = getChildCount();
        int bottomOfBottomChild = getChildAt(childCount - 1).getBottom();

        if (showingBottomFadingEdge()) {
            // leave room for bottom fading edge as long as rect isn't at very bottom
            if ((mSelectedPosition < mItemCount - 1)
                    || (rect.bottom < (bottomOfBottomChild - fadingEdge))) {
                listUnfadedBottom -= fadingEdge;
            }
        }

        int scrollYDelta = 0;

        if (rect.bottom > listUnfadedBottom && rect.top > listUnfadedTop) {
            // need to MOVE DOWN to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.height() > height) {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - listUnfadedTop);
            } else {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - listUnfadedBottom);
            }

            // make sure we aren't scrolling beyond the end of our children
            int distanceToBottom = bottomOfBottomChild - listUnfadedBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
        } else if (rect.top < listUnfadedTop && rect.bottom < listUnfadedBottom) {
            // need to MOVE UP to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.height() > height) {
                // screen size chunk
                scrollYDelta -= (listUnfadedBottom - rect.bottom);
            } else {
                // entire rect at top
                scrollYDelta -= (listUnfadedTop - rect.top);
            }

            // make sure we aren't scrolling any further than the top our children
            int top = getChildAt(0).getTop();
            int deltaToTop = top - listUnfadedTop;
            scrollYDelta = Math.max(scrollYDelta, deltaToTop);
        }

        final boolean scroll = scrollYDelta != 0;
        if (scroll) {
            scrollListItemsBy(-scrollYDelta);
            positionSelector(child);
            mSelectedTop = child.getTop();
            invalidate();
        }
        return scroll;
    }

    @Override
    void fillGap(boolean down) {
        final int count = getChildCount();
        if (down) {
            final int startOffset = count > 0 ? getChildAt(count - 1).getBottom() + mDividerHeight :
                    getListPaddingTop();
            fillDown(mFirstPosition + count, startOffset);
            correctTooHigh(getChildCount());
        } else {
            final int startOffset = count > 0 ? getChildAt(0).getTop() - mDividerHeight :
                    getHeight() - getListPaddingBottom();
            fillUp(mFirstPosition - 1, startOffset);
            correctTooLow(getChildCount());
        }
    }

    private View fillDown(int pos, int nextTop) {
        View selectedView = null;

        int end = (getBottom() - getTop()) - mListPadding.bottom;

        while (nextTop < end && pos < mItemCount) {
            // is this the selected item?
            boolean selected = pos == mSelectedPosition;
            View child = makeAndAddView(pos, nextTop, true, mListPadding.left, selected);

            nextTop = child.getBottom() + mDividerHeight;
            if (selected) {
                selectedView = child;
            }
            pos++;
        }

        return selectedView;
    }

    private View fillUp(int pos, int nextBottom) {
        View selectedView = null;

        int end = mListPadding.top;

        while (nextBottom > end && pos >= 0) {
            // is this the selected item?
            boolean selected = pos == mSelectedPosition;
            View child = makeAndAddView(pos, nextBottom, false, mListPadding.left, selected);
            nextBottom = child.getTop() - mDividerHeight;
            if (selected) {
                selectedView = child;
            }
            pos--;
        }

        mFirstPosition = pos + 1;

        return selectedView;
    }

    private View fillFromTop(int nextTop) {
        mFirstPosition = Math.min(mFirstPosition, mSelectedPosition);
        mFirstPosition = Math.min(mFirstPosition, mItemCount - 1);
        if (mFirstPosition < 0) {
            mFirstPosition = 0;
        }
        return fillDown(mFirstPosition, nextTop);
    }

    private View fillFromMiddle(int childrenTop, int childrenBottom) {
        int height = childrenBottom - childrenTop;

        int position = reconcileSelectedPosition();

        View sel = makeAndAddView(position, childrenTop, true,
                mListPadding.left, true);
        mFirstPosition = position;

        int selHeight = sel.getMeasuredHeight();
        if (selHeight <= height) {
            sel.offsetTopAndBottom((height - selHeight) / 2);
        }

        fillAboveAndBelow(sel, position);

        if (!mStackFromBottom) {
            correctTooHigh(getChildCount());
        } else {
            correctTooLow(getChildCount());
        }

        return sel;
    }

    private void fillAboveAndBelow(View sel, int position) {
        final int dividerHeight = mDividerHeight;
        if (!mStackFromBottom) {
            fillUp(position - 1, sel.getTop() - dividerHeight);
            adjustViewsUpOrDown();
            fillDown(position + 1, sel.getBottom() + dividerHeight);
        } else {
            fillDown(position + 1, sel.getBottom() + dividerHeight);
            adjustViewsUpOrDown();
            fillUp(position - 1, sel.getTop() - dividerHeight);
        }
    }

    private View fillFromSelection(int selectedTop, int childrenTop, int childrenBottom) {
        int fadingEdgeLength = getVerticalFadingEdgeLength();
        final int selectedPosition = mSelectedPosition;

        View sel;

        final int topSelectionPixel = getTopSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);
        final int bottomSelectionPixel = getBottomSelectionPixel(childrenBottom, fadingEdgeLength,
                selectedPosition);

        sel = makeAndAddView(selectedPosition, selectedTop, true, mListPadding.left, true);


        // Some of the newly selected item extends below the bottom of the list
        if (sel.getBottom() > bottomSelectionPixel) {
            // Find space available above the selection into which we can scroll
            // upwards
            final int spaceAbove = sel.getTop() - topSelectionPixel;

            // Find space required to bring the bottom of the selected item
            // fully into view
            final int spaceBelow = sel.getBottom() - bottomSelectionPixel;
            final int offset = Math.min(spaceAbove, spaceBelow);

            // Now offset the selected item to get it into view
            sel.offsetTopAndBottom(-offset);
        } else if (sel.getTop() < topSelectionPixel) {
            // Find space required to bring the top of the selected item fully
            // into view
            final int spaceAbove = topSelectionPixel - sel.getTop();

            // Find space available below the selection into which we can scroll
            // downwards
            final int spaceBelow = bottomSelectionPixel - sel.getBottom();
            final int offset = Math.min(spaceAbove, spaceBelow);

            // Offset the selected item to get it into view
            sel.offsetTopAndBottom(offset);
        }

        // Fill in views above and below
        fillAboveAndBelow(sel, selectedPosition);

        if (!mStackFromBottom) {
            correctTooHigh(getChildCount());
        } else {
            correctTooLow(getChildCount());
        }

        return sel;
    }

    private int getBottomSelectionPixel(int childrenBottom, int fadingEdgeLength,
                                        int selectedPosition) {
        int bottomSelectionPixel = childrenBottom;
        if (selectedPosition != mItemCount - 1) {
            bottomSelectionPixel -= fadingEdgeLength;
        }
        return bottomSelectionPixel;
    }

    private int getTopSelectionPixel(int childrenTop, int fadingEdgeLength, int selectedPosition) {
        // first pixel we can draw the selection into
        int topSelectionPixel = childrenTop;
        if (selectedPosition > 0) {
            topSelectionPixel += fadingEdgeLength;
        }
        return topSelectionPixel;
    }

    private View moveSelection(View oldSel, View newSel, int delta, int childrenTop,
                               int childrenBottom) {
        int fadingEdgeLength = getVerticalFadingEdgeLength();
        final int selectedPosition = mSelectedPosition;

        View sel;

        final int topSelectionPixel = getTopSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);
        final int bottomSelectionPixel = getBottomSelectionPixel(childrenTop, fadingEdgeLength,
                selectedPosition);

        if (delta > 0) {
            /*
             * Case 1: Scrolling down.
             */

            /*
             *     Before           After
             *    |       |        |       |
             *    +-------+        +-------+
             *    |   A   |        |   A   |
             *    |   1   |   =>   +-------+
             *    +-------+        |   B   |
             *    |   B   |        |   2   |
             *    +-------+        +-------+
             *    |       |        |       |
             *
             *    Try to keep the top of the previously selected item where it was.
             *    oldSel = A
             *    sel = B
             */

            // Put oldSel (A) where it belongs
            oldSel = makeAndAddView(selectedPosition - 1, oldSel.getTop(), true,
                    mListPadding.left, false);

            final int dividerHeight = mDividerHeight;

            // Now put the new selection (B) below that
            sel = makeAndAddView(selectedPosition, oldSel.getBottom() + dividerHeight, true,
                    mListPadding.left, true);

            // Some of the newly selected item extends below the bottom of the list
            if (sel.getBottom() > bottomSelectionPixel) {

                // Find space available above the selection into which we can scroll upwards
                int spaceAbove = sel.getTop() - topSelectionPixel;

                // Find space required to bring the bottom of the selected item fully into view
                int spaceBelow = sel.getBottom() - bottomSelectionPixel;

                // Don't scroll more than half the height of the list
                int halfVerticalSpace = (childrenBottom - childrenTop) / 2;
                int offset = Math.min(spaceAbove, spaceBelow);
                offset = Math.min(offset, halfVerticalSpace);

                // We placed oldSel, so offset that item
                oldSel.offsetTopAndBottom(-offset);
                // Now offset the selected item to get it into view
                sel.offsetTopAndBottom(-offset);
            }

            // Fill in views above and below
            if (!mStackFromBottom) {
                fillUp(mSelectedPosition - 2, sel.getTop() - dividerHeight);
                adjustViewsUpOrDown();
                fillDown(mSelectedPosition + 1, sel.getBottom() + dividerHeight);
            } else {
                fillDown(mSelectedPosition + 1, sel.getBottom() + dividerHeight);
                adjustViewsUpOrDown();
                fillUp(mSelectedPosition - 2, sel.getTop() - dividerHeight);
            }
        } else if (delta < 0) {
            /*
             * Case 2: Scrolling up.
             */

            /*
             *     Before           After
             *    |       |        |       |
             *    +-------+        +-------+
             *    |   A   |        |   A   |
             *    +-------+   =>   |   1   |
             *    |   B   |        +-------+
             *    |   2   |        |   B   |
             *    +-------+        +-------+
             *    |       |        |       |
             *
             *    Try to keep the top of the item about to become selected where it was.
             *    newSel = A
             *    olSel = B
             */

            if (newSel != null) {
                // Try to position the top of newSel (A) where it was before it was selected
                sel = makeAndAddView(selectedPosition, newSel.getTop(), true, mListPadding.left,
                        true);
            } else {
                // If (A) was not on screen and so did not have a view, position
                // it above the oldSel (B)
                sel = makeAndAddView(selectedPosition, oldSel.getTop(), false, mListPadding.left,
                        true);
            }

            // Some of the newly selected item extends above the top of the list
            if (sel.getTop() < topSelectionPixel) {
                // Find space required to bring the top of the selected item fully into view
                int spaceAbove = topSelectionPixel - sel.getTop();

                // Find space available below the selection into which we can scroll downwards
                int spaceBelow = bottomSelectionPixel - sel.getBottom();

                // Don't scroll more than half the height of the list
                int halfVerticalSpace = (childrenBottom - childrenTop) / 2;
                int offset = Math.min(spaceAbove, spaceBelow);
                offset = Math.min(offset, halfVerticalSpace);

                // Offset the selected item to get it into view
                sel.offsetTopAndBottom(offset);
            }

            // Fill in views above and below
            fillAboveAndBelow(sel, selectedPosition);
        } else {

            int oldTop = oldSel.getTop();

            /*
             * Case 3: Staying still
             */
            sel = makeAndAddView(selectedPosition, oldTop, true, mListPadding.left, true);

            // We're staying still...
            if (oldTop < childrenTop) {
                // ... but the top of the old selection was off screen.
                // (This can happen if the data changes size out from under us)
                int newBottom = sel.getBottom();
                if (newBottom < childrenTop + 20) {
                    // Not enough visible -- bring it onscreen
                    sel.offsetTopAndBottom(childrenTop - sel.getTop());
                }
            }

            // Fill in views above and below
            fillAboveAndBelow(sel, selectedPosition);
        }

        return sel;
    }

    private class FocusSelector implements Runnable {
        private int mPosition;
        private int mPositionTop;

        public FocusSelector setup(int position, int top) {
            mPosition = position;
            mPositionTop = top;
            return this;
        }

        public void run() {
            setSelectionFromTop(mPosition, mPositionTop);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (getChildCount() > 0) {
            View focusedChild = getFocusedChild();
            if (focusedChild != null) {
                final int childPosition = mFirstPosition + indexOfChild(focusedChild);
                final int childBottom = focusedChild.getBottom();
                final int offset = Math.max(0, childBottom - (h - getPaddingTop()));
                final int top = focusedChild.getTop() - offset;
                if (mFocusSelector == null) {
                    mFocusSelector = new FocusSelector();
                }
                post(mFocusSelector.setup(childPosition, top));
            }
        }
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Sets up mListPadding
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

        int childWidth = 0;
        int childHeight = 0;

        mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mItemCount > 0 && (widthMode == View.MeasureSpec.UNSPECIFIED ||
                heightMode == View.MeasureSpec.UNSPECIFIED)) {
            final View child = obtainView(0, mIsScrap);

            measureScrapChild(child, 0, widthMeasureSpec);

            childWidth = child.getMeasuredWidth();
            childHeight = child.getMeasuredHeight();

            if (recycleOnMeasure() && mRecycler.shouldRecycleViewType(
                    ((LayoutParams) child.getLayoutParams()).viewType)) {
                mRecycler.addScrapView(child);
            }
        }

        if (widthMode == View.MeasureSpec.UNSPECIFIED) {
            widthSize = mListPadding.left + mListPadding.right + childWidth +
                    getVerticalScrollbarWidth();
        }

        if (heightMode == View.MeasureSpec.UNSPECIFIED) {
            heightSize = mListPadding.top + mListPadding.bottom + childHeight +
                    getVerticalFadingEdgeLength() * 2;
        }

        if (heightMode == View.MeasureSpec.AT_MOST) {
            // TODO: after first layout we should maybe start at the first visible position, not 0
            heightSize = measureHeightOfChildren(widthMeasureSpec, 0, NO_POSITION, heightSize, -1);
        }

        setMeasuredDimension(widthSize, heightSize);
        mWidthMeasureSpec = widthMeasureSpec;
    }

    private void measureScrapChild(View child, int position, int widthMeasureSpec) {
        LayoutParams p = (LayoutParams) child.getLayoutParams();
        if (p == null) {
            p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0);
            child.setLayoutParams(p);
        }
        p.viewType = mAdapter.getItemViewType(position);
        p.forceAdd = true;

        int childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                mListPadding.left + mListPadding.right, p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = View.MeasureSpec.makeMeasureSpec(lpHeight, View.MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    protected boolean recycleOnMeasure() {
        return true;
    }

    final int measureHeightOfChildren(int widthMeasureSpec, int startPosition, int endPosition,
                                      final int maxHeight, int disallowPartialChildPosition) {

        final ListAdapter adapter = mAdapter;
        if (adapter == null) {
            return mListPadding.top + mListPadding.bottom;
        }

        // Include the padding of the list
        int returnedHeight = mListPadding.top + mListPadding.bottom;
        final int dividerHeight = ((mDividerHeight > 0) && mDivider != null) ? mDividerHeight : 0;
        // The previous height value that was less than maxHeight and contained
        // no partial children
        int prevHeightWithoutPartialChild = 0;
        int i;
        View child;

        // mItemCount - 1 since endPosition parameter is inclusive
        endPosition = (endPosition == NO_POSITION) ? adapter.getCount() - 1 : endPosition;
        final AbsListView.RecycleBin recycleBin = mRecycler;
        final boolean recyle = recycleOnMeasure();
        final boolean[] isScrap = mIsScrap;

        for (i = startPosition; i <= endPosition; ++i) {
            child = obtainView(i, isScrap);

            measureScrapChild(child, i, widthMeasureSpec);

            if (i > 0) {
                // Count the divider for all but one child
                returnedHeight += dividerHeight;
            }

            // Recycle the view before we possibly return from the method
            if (recyle && recycleBin.shouldRecycleViewType(
                    ((LayoutParams) child.getLayoutParams()).viewType)) {
                recycleBin.addScrapView(child);
            }

            returnedHeight += child.getMeasuredHeight();

            if (returnedHeight >= maxHeight) {
                // We went over, figure out which height to return.  If returnedHeight > maxHeight,
                // then the i'th position did not fit completely.
                return (disallowPartialChildPosition >= 0) // Disallowing is enabled (> -1)
                        && (i > disallowPartialChildPosition) // We've past the min pos
                        && (prevHeightWithoutPartialChild > 0) // We have a prev height
                        && (returnedHeight != maxHeight) // i'th child did not fit completely
                        ? prevHeightWithoutPartialChild
                        : maxHeight;
            }

            if ((disallowPartialChildPosition >= 0) && (i >= disallowPartialChildPosition)) {
                prevHeightWithoutPartialChild = returnedHeight;
            }
        }

        // At this point, we went through the range of children, and they each
        // completely fit, so return the returnedHeight
        return returnedHeight;
    }

    @Override
    int findMotionRow(int y) {
        int childCount = getChildCount();
        if (childCount > 0) {
            if (!mStackFromBottom) {
                for (int i = 0; i < childCount; i++) {
                    View v = getChildAt(i);
                    if (y <= v.getBottom()) {
                        return mFirstPosition + i;
                    }
                }
            } else {
                for (int i = childCount - 1; i >= 0; i--) {
                    View v = getChildAt(i);
                    if (y >= v.getTop()) {
                        return mFirstPosition + i;
                    }
                }
            }
        }
        return INVALID_POSITION;
    }

    private View fillSpecific(int position, int top) {
        boolean tempIsSelected = position == mSelectedPosition;
        View temp = makeAndAddView(position, top, true, mListPadding.left, tempIsSelected);
        // Possibly changed again in fillUp if we add rows above this one.
        mFirstPosition = position;

        View above;
        View below;

        final int dividerHeight = mDividerHeight;
        if (!mStackFromBottom) {
            above = fillUp(position - 1, temp.getTop() - dividerHeight);
            // This will correct for the top of the first view not touching the top of the list
            adjustViewsUpOrDown();
            below = fillDown(position + 1, temp.getBottom() + dividerHeight);
            int childCount = getChildCount();
            if (childCount > 0) {
                correctTooHigh(childCount);
            }
        } else {
            below = fillDown(position + 1, temp.getBottom() + dividerHeight);
            // This will correct for the bottom of the last view not touching the bottom of the list
            adjustViewsUpOrDown();
            above = fillUp(position - 1, temp.getTop() - dividerHeight);
            int childCount = getChildCount();
            if (childCount > 0) {
                correctTooLow(childCount);
            }
        }

        if (tempIsSelected) {
            return temp;
        } else if (above != null) {
            return above;
        } else {
            return below;
        }
    }

    private void correctTooHigh(int childCount) {
        // First see if the last item is visible. If it is not, it is OK for the
        // top of the list to be pushed up.
        int lastPosition = mFirstPosition + childCount - 1;
        if (lastPosition == mItemCount - 1 && childCount > 0) {

            // Get the last child ...
            final View lastChild = getChildAt(childCount - 1);

            // ... and its bottom edge
            final int lastBottom = lastChild.getBottom();

            // This is bottom of our drawable area
            final int end = (getBottom() - getTop()) - mListPadding.bottom;

            // This is how far the bottom edge of the last view is from the bottom of the
            // drawable area
            int bottomOffset = end - lastBottom;
            View firstChild = getChildAt(0);
            final int firstTop = firstChild.getTop();

            // Make sure we are 1) Too high, and 2) Either there are more rows above the
            // first row or the first row is scrolled off the top of the drawable area
            if (bottomOffset > 0 && (mFirstPosition > 0 || firstTop < mListPadding.top))  {
                if (mFirstPosition == 0) {
                    // Don't pull the top too far down
                    bottomOffset = Math.min(bottomOffset, mListPadding.top - firstTop);
                }
                // Move everything down
                ViewUtils.offsetChildrenTopAndBottom(this, bottomOffset);
                if (mFirstPosition > 0) {
                    // Fill the gap that was opened above mFirstPosition with more rows, if
                    // possible
                    fillUp(mFirstPosition - 1, firstChild.getTop() - mDividerHeight);
                    // Close up the remaining gap
                    adjustViewsUpOrDown();
                }

            }
        }
    }

    private void correctTooLow(int childCount) {
        // First see if the first item is visible. If it is not, it is OK for the
        // bottom of the list to be pushed down.
        if (mFirstPosition == 0 && childCount > 0) {

            // Get the first child ...
            final View firstChild = getChildAt(0);

            // ... and its top edge
            final int firstTop = firstChild.getTop();

            // This is top of our drawable area
            final int start = mListPadding.top;

            // This is bottom of our drawable area
            final int end = (getBottom() - getTop()) - mListPadding.bottom;

            // This is how far the top edge of the first view is from the top of the
            // drawable area
            int topOffset = firstTop - start;
            View lastChild = getChildAt(childCount - 1);
            final int lastBottom = lastChild.getBottom();
            int lastPosition = mFirstPosition + childCount - 1;

            // Make sure we are 1) Too low, and 2) Either there are more rows below the
            // last row or the last row is scrolled off the bottom of the drawable area
            if (topOffset > 0) {
                if (lastPosition < mItemCount - 1 || lastBottom > end)  {
                    if (lastPosition == mItemCount - 1) {
                        // Don't pull the bottom too far up
                        topOffset = Math.min(topOffset, lastBottom - end);
                    }
                    // Move everything up
                    ViewUtils.offsetChildrenTopAndBottom(this, -topOffset);
                    if (lastPosition < mItemCount - 1) {
                        // Fill the gap that was opened below the last position with more rows, if
                        // possible
                        fillDown(lastPosition + 1, lastChild.getBottom() + mDividerHeight);
                        // Close up the remaining gap
                        adjustViewsUpOrDown();
                    }
                } else if (lastPosition == mItemCount - 1) {
                    adjustViewsUpOrDown();
                }
            }
        }
    }

    @Override
    protected void layoutChildren() {
        final boolean blockLayoutRequests = mBlockLayoutRequests;
        if (!blockLayoutRequests) {
            mBlockLayoutRequests = true;
        } else {
            return;
        }

        try {
            super.layoutChildren();

            invalidate();

            if (mAdapter == null) {
                resetList();
                invokeOnItemScrollListener();
                return;
            }

            int childrenTop = mListPadding.top;
            int childrenBottom = getBottom() - getTop() - mListPadding.bottom;

            int childCount = getChildCount();
            int index = 0;
            int delta = 0;

            View sel;
            View oldSel = null;
            View oldFirst = null;
            View newSel = null;

            View focusLayoutRestoreView = null;

            // Remember stuff we will need down below
            switch (mLayoutMode) {
                case LAYOUT_SET_SELECTION:
                    index = mNextSelectedPosition - mFirstPosition;
                    if (index >= 0 && index < childCount) {
                        newSel = getChildAt(index);
                    }
                    break;
                case LAYOUT_FORCE_TOP:
                case LAYOUT_FORCE_BOTTOM:
                case LAYOUT_SPECIFIC:
                case LAYOUT_SYNC:
                    break;
                case LAYOUT_MOVE_SELECTION:
                default:
                    // Remember the previously selected view
                    index = mSelectedPosition - mFirstPosition;
                    if (index >= 0 && index < childCount) {
                        oldSel = getChildAt(index);
                    }

                    // Remember the previous first child
                    oldFirst = getChildAt(0);

                    if (mNextSelectedPosition >= 0) {
                        delta = mNextSelectedPosition - mSelectedPosition;
                    }

                    // Caution: newSel might be null
                    newSel = getChildAt(index + delta);
            }


            boolean dataChanged = mDataChanged;
            if (dataChanged) {
                handleDataChanged();
            }

            // Handle the empty set by removing all views that are visible
            // and calling it a day
            if (mItemCount == 0) {
                resetList();
                invokeOnItemScrollListener();
                return;
            } else if (mItemCount != mAdapter.getCount()) {
                throw new IllegalStateException("The content of the adapter has changed but "
                        + "ListView did not receive a notification. Make sure the content of "
                        + "your adapter is not modified from a background thread, but only "
                        + "from the UI thread. [in ListView(" + getId() + ", " + getClass()
                        + ") with Adapter(" + mAdapter.getClass() + ")]");
            }

            setSelectedPositionInt(mNextSelectedPosition);

            // Pull all children into the RecycleBin.
            // These views will be reused if possible
            final int firstPosition = mFirstPosition;
            final RecycleBin recycleBin = mRecycler;

            // reset the focus restoration
            View focusLayoutRestoreDirectChild = null;


            // Don't put header or footer views into the Recycler. Those are
            // already cached in mHeaderViews;
            if (dataChanged) {
                for (int i = 0; i < childCount; i++) {
                    recycleBin.addScrapView(getChildAt(i));
                }
            } else {
                recycleBin.fillActiveViews(childCount, firstPosition);
            }

            // take focus back to us temporarily to avoid the eventual
            // call to clear focus when removing the focused child below
            // from messing things up when ViewRoot assigns focus back
            // to someone else
            final View focusedChild = getFocusedChild();
            if (focusedChild != null) {
                // TODO: in some cases focusedChild.getParent() == null

                // we can remember the focused view to restore after relayout if the
                // data hasn't changed, or if the focused position is a header or footer
                if (!dataChanged || isDirectChildHeaderOrFooter(focusedChild)) {
                    focusLayoutRestoreDirectChild = focusedChild;
                    // remember the specific view that had focus
                    focusLayoutRestoreView = findFocus();
                    if (focusLayoutRestoreView != null) {
                        // tell it we are going to mess with it
                        focusLayoutRestoreView.onStartTemporaryDetach();
                    }
                }
                requestFocus();
            }

            // Clear out old views
            detachAllViewsFromParent();

            switch (mLayoutMode) {
                case LAYOUT_SET_SELECTION:
                    if (newSel != null) {
                        sel = fillFromSelection(newSel.getTop(), childrenTop, childrenBottom);
                    } else {
                        sel = fillFromMiddle(childrenTop, childrenBottom);
                    }
                    break;
                case LAYOUT_SYNC:
                    sel = fillSpecific(mSyncPosition, mSpecificTop);
                    break;
                case LAYOUT_FORCE_BOTTOM:
                    sel = fillUp(mItemCount - 1, childrenBottom);
                    adjustViewsUpOrDown();
                    break;
                case LAYOUT_FORCE_TOP:
                    mFirstPosition = 0;
                    sel = fillFromTop(childrenTop);
                    adjustViewsUpOrDown();
                    break;
                case LAYOUT_SPECIFIC:
                    sel = fillSpecific(reconcileSelectedPosition(), mSpecificTop);
                    break;
                case LAYOUT_MOVE_SELECTION:
                    sel = moveSelection(oldSel, newSel, delta, childrenTop, childrenBottom);
                    break;
                default:
                    if (childCount == 0) {
                        if (!mStackFromBottom) {
                            final int position = lookForSelectablePosition(0, true);
                            setSelectedPositionInt(position);
                            sel = fillFromTop(childrenTop);
                        } else {
                            final int position = lookForSelectablePosition(mItemCount - 1, false);
                            setSelectedPositionInt(position);
                            sel = fillUp(mItemCount - 1, childrenBottom);
                        }
                    } else {
                        if (mSelectedPosition >= 0 && mSelectedPosition < mItemCount) {
                            sel = fillSpecific(mSelectedPosition,
                                    oldSel == null ? childrenTop : oldSel.getTop());
                        } else if (mFirstPosition < mItemCount) {
                            sel = fillSpecific(mFirstPosition,
                                    oldFirst == null ? childrenTop : oldFirst.getTop());
                        } else {
                            sel = fillSpecific(0, childrenTop);
                        }
                    }
                    break;
            }

            // Flush any cached views that did not get reused above
            recycleBin.scrapActiveViews();

            if (sel != null) {
                // the current selected item should get focus if items
                // are focusable
                if (mItemsCanFocus && hasFocus() && !sel.hasFocus()) {
                    final boolean focusWasTaken = (sel == focusLayoutRestoreDirectChild &&
                            focusLayoutRestoreView.requestFocus()) || sel.requestFocus();
                    if (!focusWasTaken) {
                        // selected item didn't take focus, fine, but still want
                        // to make sure something else outside of the selected view
                        // has focus
                        final View focused = getFocusedChild();
                        if (focused != null) {
                            focused.clearFocus();
                        }
                        positionSelector(sel);
                    } else {
                        sel.setSelected(false);
                        mSelectorRect.setEmpty();
                    }
                } else {
                    positionSelector(sel);
                }
                mSelectedTop = sel.getTop();
            } else {
                if (mTouchMode > TOUCH_MODE_DOWN && mTouchMode < TOUCH_MODE_SCROLL) {
                    View child = getChildAt(mMotionPosition - mFirstPosition);
                    if (child != null) positionSelector(child);
                } else {
                    mSelectedTop = 0;
                    mSelectorRect.setEmpty();
                }

                // even if there is not selected position, we may need to restore
                // focus (i.e. something focusable in touch mode)
                if (hasFocus() && focusLayoutRestoreView != null) {
                    focusLayoutRestoreView.requestFocus();
                }
            }

            // tell focus view we are done mucking with it, if it is still in
            // our view hierarchy.
            if (focusLayoutRestoreView != null
                    && focusLayoutRestoreView.getWindowToken() != null) {
                focusLayoutRestoreView.onFinishTemporaryDetach();
            }

            mLayoutMode = LAYOUT_NORMAL;
            mDataChanged = false;
            mNeedSync = false;
            setNextSelectedPositionInt(mSelectedPosition);

            if (mItemCount > 0) {
                checkSelectionChanged();
            }

            invokeOnItemScrollListener();
        } finally {
            mBlockLayoutRequests = false;
        }
    }

    private boolean isDirectChildHeaderOrFooter(View child) {

        final ArrayList<FixedViewInfo> headers = mHeaderViewInfos;
        final int numHeaders = headers.size();
        for (int i = 0; i < numHeaders; i++) {
            if (child == headers.get(i).view) {
                return true;
            }
        }
        final ArrayList<FixedViewInfo> footers = mFooterViewInfos;
        final int numFooters = footers.size();
        for (int i = 0; i < numFooters; i++) {
            if (child == footers.get(i).view) {
                return true;
            }
        }
        return false;
    }

    private View makeAndAddView(int position, int y, boolean flow, int childrenLeft,
                                boolean selected) {
        View child;


        if (!mDataChanged) {
            // Try to use an exsiting view for this position
            child = mRecycler.getActiveView(position);
            if (child != null) {
                // Found it -- we're using an existing child
                // This just needs to be positioned
                setupChild(child, position, y, flow, childrenLeft, selected, true);

                return child;
            }
        }

        // Make a new view for this position, or convert an unused view if possible
        child = obtainView(position, mIsScrap);

        // This needs to be positioned and measured
        setupChild(child, position, y, flow, childrenLeft, selected, mIsScrap[0]);

        return child;
    }

    private void setupChild(View child, int position, int y, boolean flowDown, int childrenLeft,
                            boolean selected, boolean recycled) {
        final boolean isSelected = selected && shouldShowSelector();
        final boolean updateChildSelected = isSelected != child.isSelected();
        final int mode = mTouchMode;
        final boolean isPressed = mode > TOUCH_MODE_DOWN && mode < TOUCH_MODE_SCROLL &&
                mMotionPosition == position;
        final boolean updateChildPressed = isPressed != child.isPressed();
        final boolean needToMeasure = !recycled || updateChildSelected || child.isLayoutRequested();

        // Respect layout params that are already in the view. Otherwise make some up...
        // noinspection unchecked
        AbsListView.LayoutParams p = (AbsListView.LayoutParams) child.getLayoutParams();
        if (p == null) {
            p = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        }
        p.viewType = mAdapter.getItemViewType(position);

        if ((recycled && !p.forceAdd) || (p.recycledHeaderFooter &&
                p.viewType == ITEM_VIEW_TYPE_HEADER_OR_FOOTER)) {
            attachViewToParent(child, flowDown ? -1 : 0, p);
        } else {
            p.forceAdd = false;
            if (p.viewType == ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                p.recycledHeaderFooter = true;
            }
            addViewInLayout(child, flowDown ? -1 : 0, p, true);
        }

        if (updateChildSelected) {
            child.setSelected(isSelected);
        }

        if (updateChildPressed) {
            child.setPressed(isPressed);
        }

        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(mCheckStates.get(position));
            }
        }

        if (needToMeasure) {
            int childWidthSpec = getChildMeasureSpec(mWidthMeasureSpec,
                    mListPadding.left + mListPadding.right, p.width);
            int lpHeight = p.height;
            int childHeightSpec;
            if (lpHeight > 0) {
                childHeightSpec = View.MeasureSpec.makeMeasureSpec(lpHeight, View.MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            }
            child.measure(childWidthSpec, childHeightSpec);
        } else {
            cleanupLayoutState(child);
        }

        final int w = child.getMeasuredWidth();
        final int h = child.getMeasuredHeight();
        final int childTop = flowDown ? y : y - h;

        if (needToMeasure) {
            final int childRight = childrenLeft + w;
            final int childBottom = childTop + h;
            child.layout(childrenLeft, childTop, childRight, childBottom);
        } else {
            child.offsetLeftAndRight(childrenLeft - child.getLeft());
            child.offsetTopAndBottom(childTop - child.getTop());
        }

        if (mCachingStarted && !child.isDrawingCacheEnabled()) {
            child.setDrawingCacheEnabled(true);
        }
    }

    @Override
    protected boolean canAnimate() {
        return super.canAnimate() && mItemCount > 0;
    }

    @Override
    public void setSelection(int position) {
        setSelectionFromTop(position, 0);
    }

    public void setSelectionFromTop(int position, int y) {
        if (mAdapter == null) {
            return;
        }

        if (!isInTouchMode()) {
            position = lookForSelectablePosition(position, true);
            if (position >= 0) {
                setNextSelectedPositionInt(position);
            }
        } else {
            mResurrectToPosition = position;
        }

        if (position >= 0) {
            mLayoutMode = LAYOUT_SPECIFIC;
            mSpecificTop = mListPadding.top + y;

            if (mNeedSync) {
                mSyncPosition = position;
                mSyncRowId = mAdapter.getItemId(position);
            }

            requestLayout();
        }
    }

    @Override
    void setSelectionInt(int position) {
        setNextSelectedPositionInt(position);
        boolean awakeScrollbars = false;

        final int selectedPosition = mSelectedPosition;

        if (selectedPosition >= 0) {
            if (position == selectedPosition - 1) {
                awakeScrollbars = true;
            } else if (position == selectedPosition + 1) {
                awakeScrollbars = true;
            }
        }

        layoutChildren();

        if (awakeScrollbars) {
            awakenScrollBars();
        }
    }

    @Override
    int lookForSelectablePosition(int position, boolean lookDown) {
        final ListAdapter adapter = mAdapter;
        if (adapter == null || isInTouchMode()) {
            return INVALID_POSITION;
        }

        final int count = adapter.getCount();
        if (!mAreAllItemsSelectable) {
            if (lookDown) {
                position = Math.max(0, position);
                while (position < count && !adapter.isEnabled(position)) {
                    position++;
                }
            } else {
                position = Math.min(position, count - 1);
                while (position >= 0 && !adapter.isEnabled(position)) {
                    position--;
                }
            }

            if (position < 0 || position >= count) {
                return INVALID_POSITION;
            }
            return position;
        } else {
            if (position < 0 || position >= count) {
                return INVALID_POSITION;
            }
            return position;
        }
    }

    public void setSelectionAfterHeaderView() {
        final int count = mHeaderViewInfos.size();
        if (count > 0) {
            mNextSelectedPosition = 0;
            return;
        }

        if (mAdapter != null) {
            setSelection(count);
        } else {
            mNextSelectedPosition = count;
            mLayoutMode = LAYOUT_SET_SELECTION;
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Dispatch in the normal way
        boolean handled = super.dispatchKeyEvent(event);
        if (!handled) {
            // If we didn't handle it...
            View focused = getFocusedChild();
            if (focused != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                // ... and our focused child didn't handle it
                // ... give it to ourselves so we can scroll if necessary
                handled = onKeyDown(event.getKeyCode(), event);
            }
        }
        return handled;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return commonKey(keyCode, 1, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return commonKey(keyCode, repeatCount, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return commonKey(keyCode, 1, event);
    }

    private boolean commonKey(int keyCode, int count, KeyEvent event) {
        if (mAdapter == null) {
            return false;
        }

        if (mDataChanged) {
            layoutChildren();
        }

        boolean handled = false;
        int action = event.getAction();

        if (action != KeyEvent.ACTION_UP) {
            if (mSelectedPosition < 0) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_SPACE:
                        if (resurrectSelection()) {
                            return true;
                        }
                }
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!event.isAltPressed()) {
                        while (count > 0) {
                            handled = arrowScroll(FOCUS_UP);
                            count--;
                        }
                    } else {
                        handled = fullScroll(FOCUS_UP);
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!event.isAltPressed()) {
                        while (count > 0) {
                            handled = arrowScroll(FOCUS_DOWN);
                            count--;
                        }
                    } else {
                        handled = fullScroll(FOCUS_DOWN);
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    handled = handleHorizontalFocusWithinListItem(FOCUS_LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    handled = handleHorizontalFocusWithinListItem(FOCUS_RIGHT);
                    break;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (mItemCount > 0 && event.getRepeatCount() == 0) {
                        keyPressed();
                    }
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_SPACE:
                    if (!event.isShiftPressed()) {
                        pageScroll(FOCUS_DOWN);
                    } else {
                        pageScroll(FOCUS_UP);
                    }
                    handled = true;
                    break;
            }
        }

        if (handled) {
            return true;
        } else {
            switch (action) {
                case KeyEvent.ACTION_DOWN:
                    return super.onKeyDown(keyCode, event);

                case KeyEvent.ACTION_UP:
                    return super.onKeyUp(keyCode, event);

                case KeyEvent.ACTION_MULTIPLE:
                    return super.onKeyMultiple(keyCode, count, event);

                default: // shouldn't happen
                    return false;
            }
        }
    }

    boolean pageScroll(int direction) {
        int nextPage = -1;
        boolean down = false;

        if (direction == FOCUS_UP) {
            nextPage = Math.max(0, mSelectedPosition - getChildCount() - 1);
        } else if (direction == FOCUS_DOWN) {
            nextPage = Math.min(mItemCount - 1, mSelectedPosition + getChildCount() - 1);
            down = true;
        }

        if (nextPage >= 0) {
            int position = lookForSelectablePosition(nextPage, down);
            if (position >= 0) {
                mLayoutMode = LAYOUT_SPECIFIC;
                mSpecificTop = getPaddingTop() + getVerticalFadingEdgeLength();

                if (down && position > mItemCount - getChildCount()) {
                    mLayoutMode = LAYOUT_FORCE_BOTTOM;
                }

                if (!down && position < getChildCount()) {
                    mLayoutMode = LAYOUT_FORCE_TOP;
                }

                setSelectionInt(position);
                invokeOnItemScrollListener();
                if (!awakenScrollBars()) {
                    invalidate();
                }

                return true;
            }
        }

        return false;
    }

    boolean fullScroll(int direction) {
        boolean moved = false;
        if (direction == FOCUS_UP) {
            if (mSelectedPosition != 0) {
                int position = lookForSelectablePosition(0, true);
                if (position >= 0) {
                    mLayoutMode = LAYOUT_FORCE_TOP;
                    setSelectionInt(position);
                    invokeOnItemScrollListener();
                }
                moved = true;
            }
        } else if (direction == FOCUS_DOWN) {
            if (mSelectedPosition < mItemCount - 1) {
                int position = lookForSelectablePosition(mItemCount - 1, true);
                if (position >= 0) {
                    mLayoutMode = LAYOUT_FORCE_BOTTOM;
                    setSelectionInt(position);
                    invokeOnItemScrollListener();
                }
                moved = true;
            }
        }

        if (moved && !awakenScrollBars()) {
            awakenScrollBars();
            invalidate();
        }

        return moved;
    }

    private boolean handleHorizontalFocusWithinListItem(int direction) {
        if (direction != FOCUS_LEFT && direction != FOCUS_RIGHT)  {
            throw new IllegalArgumentException("direction must be one of"
                    + " {View.FOCUS_LEFT, View.FOCUS_RIGHT}");
        }

        final int numChildren = getChildCount();
        if (mItemsCanFocus && numChildren > 0 && mSelectedPosition != INVALID_POSITION) {
            final View selectedView = getSelectedView();
            if (selectedView != null && selectedView.hasFocus() &&
                    selectedView instanceof ViewGroup) {

                final View currentFocus = selectedView.findFocus();
                final View nextFocus = FocusFinder.getInstance().findNextFocus(
                        (ViewGroup) selectedView, currentFocus, direction);
                if (nextFocus != null) {
                    // do the math to get interesting rect in next focus' coordinates
                    currentFocus.getFocusedRect(mTempRect);
                    offsetDescendantRectToMyCoords(currentFocus, mTempRect);
                    offsetRectIntoDescendantCoords(nextFocus, mTempRect);
                    if (nextFocus.requestFocus(direction, mTempRect)) {
                        return true;
                    }
                }
                // we are blocking the key from being handled (by returning true)
                // if the global result is going to be some other view within this
                // list.  this is to acheive the overall goal of having
                // horizontal d-pad navigation remain in the current item.
                final View globalNextFocus = FocusFinder.getInstance().findNextFocus(
                        (ViewGroup) getRootView(), currentFocus, direction);
                if (globalNextFocus != null) {
                    return isViewAncestorOf(globalNextFocus, this);
                }
            }
        }
        return false;
    }

    boolean arrowScroll(int direction) {
        try {
            mInLayout = true;
            final boolean handled = arrowScrollImpl(direction);
            if (handled) {
                playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
            }
            return handled;
        } finally {
            mInLayout = false;
        }
    }

    private boolean arrowScrollImpl(int direction) {
        if (getChildCount() <= 0) {
            return false;
        }

        View selectedView = getSelectedView();

        int nextSelectedPosition = lookForSelectablePositionOnScreen(direction);
        int amountToScroll = amountToScroll(direction, nextSelectedPosition);

        // if we are moving focus, we may OVERRIDE the default behavior
        final ArrowScrollFocusResult focusResult = mItemsCanFocus ? arrowScrollFocused(direction) : null;
        if (focusResult != null) {
            nextSelectedPosition = focusResult.getSelectedPosition();
            amountToScroll = focusResult.getAmountToScroll();
        }

        boolean needToRedraw = focusResult != null;
        if (nextSelectedPosition != INVALID_POSITION) {
            handleNewSelectionChange(selectedView, direction, nextSelectedPosition, focusResult != null);
            setSelectedPositionInt(nextSelectedPosition);
            setNextSelectedPositionInt(nextSelectedPosition);
            selectedView = getSelectedView();
            if (mItemsCanFocus && focusResult == null) {
                // there was no new view found to take focus, make sure we
                // don't leave focus with the old selection
                final View focused = getFocusedChild();
                if (focused != null) {
                    focused.clearFocus();
                }
            }
            needToRedraw = true;
            checkSelectionChanged();
        }

        if (amountToScroll > 0) {
            scrollListItemsBy((direction == FOCUS_UP) ? amountToScroll : -amountToScroll);
            needToRedraw = true;
        }

        // if we didn't find a new focusable, make sure any existing focused
        // item that was panned off screen gives up focus.
        if (mItemsCanFocus && (focusResult == null)
                && selectedView != null && selectedView.hasFocus()) {
            final View focused = selectedView.findFocus();
            if (distanceToView(focused) > 0) {
                focused.clearFocus();
            }
        }

        // if  the current selection is panned off, we need to remove the selection
        if (nextSelectedPosition == INVALID_POSITION && selectedView != null
                && !isViewAncestorOf(selectedView, this)) {
            selectedView = null;
            hideSelector();

            // but we don't want to set the ressurect position (that would make subsequent
            // unhandled key events bring back the item we just scrolled off!)
            mResurrectToPosition = INVALID_POSITION;
        }

        if (needToRedraw) {
            if (selectedView != null) {
                positionSelector(selectedView);
                mSelectedTop = selectedView.getTop();
            }
            if (!awakenScrollBars()) {
                invalidate();
            }
            invokeOnItemScrollListener();
            return true;
        }

        return false;
    }

    private void handleNewSelectionChange(View selectedView, int direction, int newSelectedPosition,
                                          boolean newFocusAssigned) {
        if (newSelectedPosition == INVALID_POSITION) {
            throw new IllegalArgumentException("newSelectedPosition needs to be valid");
        }

        // whether or not we are moving down or up, we want to preserve the
        // top of whatever view is on top:
        // - moving down: the view that had selection
        // - moving up: the view that is getting selection
        View topView;
        View bottomView;
        int topViewIndex, bottomViewIndex;
        boolean topSelected = false;
        final int selectedIndex = mSelectedPosition - mFirstPosition;
        final int nextSelectedIndex = newSelectedPosition - mFirstPosition;
        if (direction == FOCUS_UP) {
            topViewIndex = nextSelectedIndex;
            bottomViewIndex = selectedIndex;
            topView = getChildAt(topViewIndex);
            bottomView = selectedView;
            topSelected = true;
        } else {
            topViewIndex = selectedIndex;
            bottomViewIndex = nextSelectedIndex;
            topView = selectedView;
            bottomView = getChildAt(bottomViewIndex);
        }

        final int numChildren = getChildCount();

        // start with top view: is it changing size?
        if (topView != null) {
            topView.setSelected(!newFocusAssigned && topSelected);
            measureAndAdjustDown(topView, topViewIndex, numChildren);
        }

        // is the bottom view changing size?
        if (bottomView != null) {
            bottomView.setSelected(!newFocusAssigned && !topSelected);
            measureAndAdjustDown(bottomView, bottomViewIndex, numChildren);
        }
    }

    private void measureAndAdjustDown(View child, int childIndex, int numChildren) {
        int oldHeight = child.getHeight();
        measureItem(child);
        if (child.getMeasuredHeight() != oldHeight) {
            // lay out the view, preserving its top
            relayoutMeasuredItem(child);

            // adjust views below appropriately
            final int heightDelta = child.getMeasuredHeight() - oldHeight;
            for (int i = childIndex + 1; i < numChildren; i++) {
                getChildAt(i).offsetTopAndBottom(heightDelta);
            }
        }
    }

    private void measureItem(View child) {
        ViewGroup.LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int childWidthSpec = getChildMeasureSpec(mWidthMeasureSpec,
                mListPadding.left + mListPadding.right, p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = View.MeasureSpec.makeMeasureSpec(lpHeight, View.MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private void relayoutMeasuredItem(View child) {
        final int w = child.getMeasuredWidth();
        final int h = child.getMeasuredHeight();
        final int childLeft = mListPadding.left;
        final int childRight = childLeft + w;
        final int childTop = child.getTop();
        final int childBottom = childTop + h;
        child.layout(childLeft, childTop, childRight, childBottom);
    }

    private int getArrowScrollPreviewLength() {
        return Math.max(MIN_SCROLL_PREVIEW_PIXELS, getVerticalFadingEdgeLength());
    }

    private int amountToScroll(int direction, int nextSelectedPosition) {
        final int listBottom = getHeight() - mListPadding.bottom;
        final int listTop = mListPadding.top;

        final int numChildren = getChildCount();

        if (direction == FOCUS_DOWN) {
            int indexToMakeVisible = numChildren - 1;
            if (nextSelectedPosition != INVALID_POSITION) {
                indexToMakeVisible = nextSelectedPosition - mFirstPosition;
            }

            final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
            final View viewToMakeVisible = getChildAt(indexToMakeVisible);

            int goalBottom = listBottom;
            if (positionToMakeVisible < mItemCount - 1) {
                goalBottom -= getArrowScrollPreviewLength();
            }

            if (viewToMakeVisible.getBottom() <= goalBottom) {
                // item is fully visible.
                return 0;
            }

            if (nextSelectedPosition != INVALID_POSITION
                    && (goalBottom - viewToMakeVisible.getTop()) >= getMaxScrollAmount()) {
                // item already has enough of it visible, changing selection is good enough
                return 0;
            }

            int amountToScroll = (viewToMakeVisible.getBottom() - goalBottom);

            if ((mFirstPosition + numChildren) == mItemCount) {
                // last is last in list -> make sure we don't scroll past it
                final int max = getChildAt(numChildren - 1).getBottom() - listBottom;
                amountToScroll = Math.min(amountToScroll, max);
            }

            return Math.min(amountToScroll, getMaxScrollAmount());
        } else {
            int indexToMakeVisible = 0;
            if (nextSelectedPosition != INVALID_POSITION) {
                indexToMakeVisible = nextSelectedPosition - mFirstPosition;
            }
            final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
            final View viewToMakeVisible = getChildAt(indexToMakeVisible);
            int goalTop = listTop;
            if (positionToMakeVisible > 0) {
                goalTop += getArrowScrollPreviewLength();
            }
            if (viewToMakeVisible.getTop() >= goalTop) {
                // item is fully visible.
                return 0;
            }

            if (nextSelectedPosition != INVALID_POSITION &&
                    (viewToMakeVisible.getBottom() - goalTop) >= getMaxScrollAmount()) {
                // item already has enough of it visible, changing selection is good enough
                return 0;
            }

            int amountToScroll = (goalTop - viewToMakeVisible.getTop());
            if (mFirstPosition == 0) {
                // first is first in list -> make sure we don't scroll past it
                final int max = listTop - getChildAt(0).getTop();
                amountToScroll = Math.min(amountToScroll,  max);
            }
            return Math.min(amountToScroll, getMaxScrollAmount());
        }
    }

    static private class ArrowScrollFocusResult {
        private int mSelectedPosition;
        private int mAmountToScroll;

        void populate(int selectedPosition, int amountToScroll) {
            mSelectedPosition = selectedPosition;
            mAmountToScroll = amountToScroll;
        }

        public int getSelectedPosition() {
            return mSelectedPosition;
        }

        public int getAmountToScroll() {
            return mAmountToScroll;
        }
    }

    private int lookForSelectablePositionOnScreen(int direction) {
        final int firstPosition = mFirstPosition;
        if (direction == FOCUS_DOWN) {
            int startPos = (mSelectedPosition != INVALID_POSITION) ?
                    mSelectedPosition + 1 :
                    firstPosition;
            if (startPos >= mAdapter.getCount()) {
                return INVALID_POSITION;
            }
            if (startPos < firstPosition) {
                startPos = firstPosition;
            }

            final int lastVisiblePos = getLastVisiblePosition();
            final ListAdapter adapter = getAdapter();
            for (int pos = startPos; pos <= lastVisiblePos; pos++) {
                if (adapter.isEnabled(pos)
                        && getChildAt(pos - firstPosition).getVisibility() == VISIBLE) {
                    return pos;
                }
            }
        } else {
            int last = firstPosition + getChildCount() - 1;
            int startPos = (mSelectedPosition != INVALID_POSITION) ?
                    mSelectedPosition - 1 :
                    firstPosition + getChildCount() - 1;
            if (startPos < 0) {
                return INVALID_POSITION;
            }
            if (startPos > last) {
                startPos = last;
            }

            final ListAdapter adapter = getAdapter();
            for (int pos = startPos; pos >= firstPosition; pos--) {
                if (adapter.isEnabled(pos)
                        && getChildAt(pos - firstPosition).getVisibility() == VISIBLE) {
                    return pos;
                }
            }
        }
        return INVALID_POSITION;
    }

    private ArrowScrollFocusResult arrowScrollFocused(final int direction) {
        final View selectedView = getSelectedView();
        View newFocus;
        if (selectedView != null && selectedView.hasFocus()) {
            View oldFocus = selectedView.findFocus();
            newFocus = FocusFinder.getInstance().findNextFocus(this, oldFocus, direction);
        } else {
            if (direction == FOCUS_DOWN) {
                final boolean topFadingEdgeShowing = (mFirstPosition > 0);
                final int listTop = mListPadding.top +
                        (topFadingEdgeShowing ? getArrowScrollPreviewLength() : 0);
                final int ySearchPoint =
                        (selectedView != null && selectedView.getTop() > listTop) ?
                                selectedView.getTop() :
                                listTop;
                mTempRect.set(0, ySearchPoint, 0, ySearchPoint);
            } else {
                final boolean bottomFadingEdgeShowing =
                        (mFirstPosition + getChildCount() - 1) < mItemCount;
                final int listBottom = getHeight() - mListPadding.bottom -
                        (bottomFadingEdgeShowing ? getArrowScrollPreviewLength() : 0);
                final int ySearchPoint =
                        (selectedView != null && selectedView.getBottom() < listBottom) ?
                                selectedView.getBottom() :
                                listBottom;
                mTempRect.set(0, ySearchPoint, 0, ySearchPoint);
            }
            newFocus = FocusFinder.getInstance().findNextFocusFromRect(this, mTempRect, direction);
        }

        if (newFocus != null) {
            final int positionOfNewFocus = positionOfNewFocus(newFocus);

            // if the focus change is in a different new position, make sure
            // we aren't jumping over another selectable position
            if (mSelectedPosition != INVALID_POSITION && positionOfNewFocus != mSelectedPosition) {
                final int selectablePosition = lookForSelectablePositionOnScreen(direction);
                if (selectablePosition != INVALID_POSITION &&
                        ((direction == FOCUS_DOWN && selectablePosition < positionOfNewFocus) ||
                                (direction == FOCUS_UP && selectablePosition > positionOfNewFocus))) {
                    return null;
                }
            }

            int focusScroll = amountToScrollToNewFocus(direction, newFocus, positionOfNewFocus);

            final int maxScrollAmount = getMaxScrollAmount();
            if (focusScroll < maxScrollAmount) {
                // not moving too far, safe to give next view focus
                newFocus.requestFocus(direction);
                mArrowScrollFocusResult.populate(positionOfNewFocus, focusScroll);
                return mArrowScrollFocusResult;
            } else if (distanceToView(newFocus) < maxScrollAmount){
                // Case to consider:
                // too far to get entire next focusable on screen, but by going
                // max scroll amount, we are getting it at least partially in view,
                // so give it focus and scroll the max ammount.
                newFocus.requestFocus(direction);
                mArrowScrollFocusResult.populate(positionOfNewFocus, maxScrollAmount);
                return mArrowScrollFocusResult;
            }
        }
        return null;
    }

    private int positionOfNewFocus(View newFocus) {
        final int numChildren = getChildCount();
        for (int i = 0; i < numChildren; i++) {
            final View child = getChildAt(i);
            if (isViewAncestorOf(newFocus, child)) {
                return mFirstPosition + i;
            }
        }
        throw new IllegalArgumentException("newFocus is not a child of any of the"
                + " children of the list!");
    }

    private boolean isViewAncestorOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewAncestorOf((View) theParent, parent);
    }

    private int amountToScrollToNewFocus(int direction, View newFocus, int positionOfNewFocus) {
        int amountToScroll = 0;
        newFocus.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(newFocus, mTempRect);
        if (direction == FOCUS_UP) {
            if (mTempRect.top < mListPadding.top) {
                amountToScroll = mListPadding.top - mTempRect.top;
                if (positionOfNewFocus > 0) {
                    amountToScroll += getArrowScrollPreviewLength();
                }
            }
        } else {
            final int listBottom = getHeight() - mListPadding.bottom;
            if (mTempRect.bottom > listBottom) {
                amountToScroll = mTempRect.bottom - listBottom;
                if (positionOfNewFocus < mItemCount - 1) {
                    amountToScroll += getArrowScrollPreviewLength();
                }
            }
        }
        return amountToScroll;
    }

    private int distanceToView(View descendant) {
        int distance = 0;
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);
        final int listBottom = getBottom() - getTop() - mListPadding.bottom;
        if (mTempRect.bottom < mListPadding.top) {
            distance = mListPadding.top - mTempRect.bottom;
        } else if (mTempRect.top > listBottom) {
            distance = mTempRect.top - listBottom;
        }
        return distance;
    }

    private void scrollListItemsBy(int amount) {
        ViewUtils.offsetChildrenTopAndBottom(this, amount);

        final int listBottom = getHeight() - mListPadding.bottom;
        final int listTop = mListPadding.top;
        final AbsListView.RecycleBin recycleBin = mRecycler;

        if (amount < 0) {
            // shifted items up

            // may need to pan views into the bottom space
            int numChildren = getChildCount();
            View last = getChildAt(numChildren - 1);
            while (last.getBottom() < listBottom) {
                final int lastVisiblePosition = mFirstPosition + numChildren - 1;
                if (lastVisiblePosition < mItemCount - 1) {
                    last = addViewBelow(last, lastVisiblePosition);
                    numChildren++;
                } else {
                    break;
                }
            }

            // may have brought in the last child of the list that is skinnier
            // than the fading edge, thereby leaving space at the end.  need
            // to shift back
            if (last.getBottom() < listBottom) {
                ViewUtils.offsetChildrenTopAndBottom(this, listBottom - last.getBottom());
            }

            // top views may be panned off screen
            View first = getChildAt(0);
            while (first.getBottom() < listTop) {
                AbsListView.LayoutParams layoutParams = (LayoutParams) first.getLayoutParams();
                if (recycleBin.shouldRecycleViewType(layoutParams.viewType)) {
                    detachViewFromParent(first);
                    recycleBin.addScrapView(first);
                } else {
                    removeViewInLayout(first);
                }
                first = getChildAt(0);
                mFirstPosition++;
            }
        } else {
            // shifted items down
            View first = getChildAt(0);

            // may need to pan views into top
            while ((first.getTop() > listTop) && (mFirstPosition > 0)) {
                first = addViewAbove(first, mFirstPosition);
                mFirstPosition--;
            }

            // may have brought the very first child of the list in too far and
            // need to shift it back
            if (first.getTop() > listTop) {
                ViewUtils.offsetChildrenTopAndBottom(this, listTop - first.getTop());
            }

            int lastIndex = getChildCount() - 1;
            View last = getChildAt(lastIndex);

            // bottom view may be panned off screen
            while (last.getTop() > listBottom) {
                AbsListView.LayoutParams layoutParams = (LayoutParams) last.getLayoutParams();
                if (recycleBin.shouldRecycleViewType(layoutParams.viewType)) {
                    detachViewFromParent(last);
                    recycleBin.addScrapView(last);
                } else {
                    removeViewInLayout(last);
                }
                last = getChildAt(--lastIndex);
            }
        }
    }

    private View addViewAbove(View theView, int position) {
        int abovePosition = position - 1;
        View view = obtainView(abovePosition, mIsScrap);
        int edgeOfNewChild = theView.getTop() - mDividerHeight;
        setupChild(view, abovePosition, edgeOfNewChild, false, mListPadding.left,
                false, mIsScrap[0]);
        return view;
    }

    private View addViewBelow(View theView, int position) {
        int belowPosition = position + 1;
        View view = obtainView(belowPosition, mIsScrap);
        int edgeOfNewChild = theView.getBottom() + mDividerHeight;
        setupChild(view, belowPosition, edgeOfNewChild, true, mListPadding.left,
                false, mIsScrap[0]);
        return view;
    }

    public void setItemsCanFocus(boolean itemsCanFocus) {
        mItemsCanFocus = itemsCanFocus;
        if (!itemsCanFocus) {
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        }
    }

    public boolean getItemsCanFocus() {
        return mItemsCanFocus;
    }

    @Override
    public void setCacheColorHint(int color) {
        final boolean opaque = (color >>> 24) == 0xFF;
        mIsCacheColorOpaque = opaque;
        if (opaque) {
            if (mDividerPaint == null) {
                mDividerPaint = new Paint();
            }
            mDividerPaint.setColor(color);
        }
        super.setCacheColorHint(color);
    }

    void drawOverscrollHeader(Canvas canvas, Drawable drawable, Rect bounds) {
        final int height = drawable.getMinimumHeight();

        canvas.save();
        canvas.clipRect(bounds);

        final int span = bounds.bottom - bounds.top;
        if (span < height) {
            bounds.top = bounds.bottom - height;
        }

        drawable.setBounds(bounds);
        drawable.draw(canvas);

        canvas.restore();
    }

    void drawOverscrollFooter(Canvas canvas, Drawable drawable, Rect bounds) {
        final int height = drawable.getMinimumHeight();

        canvas.save();
        canvas.clipRect(bounds);

        final int span = bounds.bottom - bounds.top;
        if (span < height) {
            bounds.bottom = bounds.top + height;
        }

        drawable.setBounds(bounds);
        drawable.draw(canvas);

        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the dividers
        final int dividerHeight = mDividerHeight;
        final Drawable overscrollHeader = mOverScrollHeader;
        final Drawable overscrollFooter = mOverScrollFooter;
        final boolean drawOverscrollHeader = overscrollHeader != null;
        final boolean drawOverscrollFooter = overscrollFooter != null;
        final boolean drawDividers = dividerHeight > 0 && mDivider != null;

        if (drawDividers || drawOverscrollHeader || drawOverscrollFooter) {
            // Only modify the top and bottom in the loop, we set the left and right here
            final Rect bounds = mTempRect;
            bounds.left = getPaddingLeft();
            bounds.right = getRight() - getLeft() - getPaddingRight();

            final int count = getChildCount();
            final int headerCount = mHeaderViewInfos.size();
            final int itemCount = mItemCount;
            final int footerLimit = itemCount - mFooterViewInfos.size() - 1;
            final boolean headerDividers = mHeaderDividersEnabled;
            final boolean footerDividers = mFooterDividersEnabled;
            final int first = mFirstPosition;
            final boolean areAllItemsSelectable = mAreAllItemsSelectable;
            final ListAdapter adapter = mAdapter;
            // If the list is opaque *and* the background is not, we want to
            // fill a rect where the dividers would be for non-selectable items
            // If the list is opaque and the background is also opaque, we don't
            // need to draw anything since the background will do it for us
            final boolean fillForMissingDividers = drawDividers && isOpaque() && !super.isOpaque();

            if (fillForMissingDividers && mDividerPaint == null && mIsCacheColorOpaque) {
                mDividerPaint = new Paint();
                mDividerPaint.setColor(getCacheColorHint());
            }
            final Paint paint = mDividerPaint;

            final int listBottom = getBottom() - getTop() - mListPadding.bottom + getScrollY();
            if (!mStackFromBottom) {
                int bottom = 0;

                // Draw top divider or header for overscroll
                final int scrollY = getScrollY();
                if (count > 0 && scrollY < 0) {
                    if (drawOverscrollHeader) {
                        bounds.bottom = 0;
                        bounds.top = scrollY;
                        drawOverscrollHeader(canvas, overscrollHeader, bounds);
                    } else if (drawDividers) {
                        bounds.bottom = 0;
                        bounds.top = -dividerHeight;
                        drawDivider(canvas, bounds, -1);
                    }
                }

                for (int i = 0; i < count; i++) {
                    if ((headerDividers || first + i >= headerCount) &&
                            (footerDividers || first + i < footerLimit)) {
                        View child = getChildAt(i);
                        bottom = child.getBottom();
                        // Don't draw dividers next to items that are not enabled
                        if (drawDividers &&
                                (bottom < listBottom && !(drawOverscrollFooter && i == count - 1))) {
                            if ((areAllItemsSelectable ||
                                    (adapter.isEnabled(first + i) && (i == count - 1 ||
                                            adapter.isEnabled(first + i + 1))))) {
                                bounds.top = bottom;
                                bounds.bottom = bottom + dividerHeight;
                                drawDivider(canvas, bounds, i);
                            } else if (fillForMissingDividers) {
                                bounds.top = bottom;
                                bounds.bottom = bottom + dividerHeight;
                                canvas.drawRect(bounds, paint);
                            }
                        }
                    }
                }

                final int overFooterBottom = getBottom() + getScrollY();
                if (drawOverscrollFooter && first + count == itemCount &&
                        overFooterBottom > bottom) {
                    bounds.top = bottom;
                    bounds.bottom = overFooterBottom;
                    drawOverscrollFooter(canvas, overscrollFooter, bounds);
                }
            } else {
                int top;
                int listTop = mListPadding.top;

                final int scrollY = getScrollY();

                if (count > 0 && drawOverscrollHeader) {
                    bounds.top = scrollY;
                    bounds.bottom = getChildAt(0).getTop();
                    drawOverscrollHeader(canvas, overscrollHeader, bounds);
                }

                final int start = drawOverscrollHeader ? 1 : 0;
                for (int i = start; i < count; i++) {
                    if ((headerDividers || first + i >= headerCount) &&
                            (footerDividers || first + i < footerLimit)) {
                        View child = getChildAt(i);
                        top = child.getTop();
                        // Don't draw dividers next to items that are not enabled
                        if (drawDividers && top > listTop) {
                            if ((areAllItemsSelectable ||
                                    (adapter.isEnabled(first + i) && (i == count - 1 ||
                                            adapter.isEnabled(first + i + 1))))) {
                                bounds.top = top - dividerHeight;
                                bounds.bottom = top;
                                // Give the method the child ABOVE the divider, so we
                                // subtract one from our child
                                // position. Give -1 when there is no child above the
                                // divider.
                                drawDivider(canvas, bounds, i - 1);
                            } else if (fillForMissingDividers) {
                                bounds.top = top - dividerHeight;
                                bounds.bottom = top;
                                canvas.drawRect(bounds, paint);
                            }
                        }
                    }
                }

                if (count > 0 && scrollY > 0) {
                    if (drawOverscrollFooter) {
                        final int absListBottom = getBottom();
                        bounds.top = absListBottom;
                        bounds.bottom = absListBottom + scrollY;
                        drawOverscrollFooter(canvas, overscrollFooter, bounds);
                    } else if (drawDividers) {
                        bounds.top = listBottom;
                        bounds.bottom = listBottom + dividerHeight;
                        drawDivider(canvas, bounds, -1);
                    }
                }
            }
        }

        // Draw the indicators (these should be drawn above the dividers) and children
        super.dispatchDraw(canvas);
    }

    void drawDivider(Canvas canvas, Rect bounds, int childIndex) {
        // This widget draws the same divider for all children
        final Drawable divider = mDivider;
        final boolean clipDivider = mClipDivider;

        if (!clipDivider) {
            divider.setBounds(bounds);
        } else {
            canvas.save();
            canvas.clipRect(bounds);
        }

        divider.draw(canvas);

        if (clipDivider) {
            canvas.restore();
        }
    }

    public Drawable getDivider() {
        return mDivider;
    }

    public void setDivider(Drawable divider) {
        if (divider != null) {
            mDividerHeight = divider.getIntrinsicHeight();
            mClipDivider = divider instanceof ColorDrawable;
        } else {
            mDividerHeight = 0;
            mClipDivider = false;
        }
        mDivider = divider;
        requestLayoutIfNecessary();
    }

    public int getDividerHeight() {
        return mDividerHeight;
    }

    public void setDividerHeight(int height) {
        mDividerHeight = height;
        requestLayoutIfNecessary();
    }

    public void setHeaderDividersEnabled(boolean headerDividersEnabled) {
        mHeaderDividersEnabled = headerDividersEnabled;
        invalidate();
    }

    public void setFooterDividersEnabled(boolean footerDividersEnabled) {
        mFooterDividersEnabled = footerDividersEnabled;
        invalidate();
    }

    public void setOverscrollHeader(Drawable header) {
        mOverScrollHeader = header;
        if (getScrollY() < 0) {
            invalidate();
        }
    }

    public Drawable getOverscrollHeader() {
        return mOverScrollHeader;
    }

    public void setOverscrollFooter(Drawable footer) {
        mOverScrollFooter = footer;
        invalidate();
    }

    public Drawable getOverscrollFooter() {
        return mOverScrollFooter;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        int closetChildIndex = -1;
        if (gainFocus && previouslyFocusedRect != null) {
            previouslyFocusedRect.offset(getScrollX(), getScrollY());

            final ListAdapter adapter = mAdapter;
            // Don't cache the result of getChildCount or mFirstPosition here,
            // it could change in layoutChildren.
            if (adapter.getCount() < getChildCount() + mFirstPosition) {
                mLayoutMode = LAYOUT_NORMAL;
                layoutChildren();
            }

            // figure out which item should be selected based on previously
            // focused rect
            Rect otherRect = mTempRect;
            int minDistance = Integer.MAX_VALUE;
            final int childCount = getChildCount();
            final int firstPosition = mFirstPosition;

            for (int i = 0; i < childCount; i++) {
                // only consider selectable views
                if (!adapter.isEnabled(firstPosition + i)) {
                    continue;
                }

                View other = getChildAt(i);
                other.getDrawingRect(otherRect);
                offsetDescendantRectToMyCoords(other, otherRect);
                int distance = getDistance(previouslyFocusedRect, otherRect, direction);

                if (distance < minDistance) {
                    minDistance = distance;
                    closetChildIndex = i;
                }
            }
        }

        if (closetChildIndex >= 0) {
            setSelection(closetChildIndex + mFirstPosition);
        } else {
            requestLayout();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int count = getChildCount();
        if (count > 0) {
            for (int i = 0; i < count; ++i) {
                addHeaderView(getChildAt(i));
            }
            removeAllViews();
        }
    }

    View findViewInHeadersOrFooters(ArrayList<FixedViewInfo> where, int id) {
        if (where != null) {
            int len = where.size();
            View v;

            for (int i = 0; i < len; i++) {
                v = where.get(i).view;

                v = v.findViewById(id);

                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    View findViewTagInHeadersOrFooters(ArrayList<FixedViewInfo> where, Object tag) {
        if (where != null) {
            int len = where.size();
            View v;

            for (int i = 0; i < len; i++) {
                v = where.get(i).view;

                v = v.findViewWithTag(tag);

                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mItemsCanFocus && ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants.
            return false;
        }
        return super.onTouchEvent(ev);
    }

    public int getChoiceMode() {
        return mChoiceMode;
    }

    public void setChoiceMode(int choiceMode) {
        mChoiceMode = choiceMode;
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (mCheckStates == null) {
                mCheckStates = new SparseBooleanArray();
            }
            if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
                mCheckedIdStates = new LongSparseArray<>();
            }
        }
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        boolean handled = false;

        if (mChoiceMode != CHOICE_MODE_NONE) {
            handled = true;

            if (mChoiceMode == CHOICE_MODE_MULTIPLE) {
                boolean newValue = !mCheckStates.get(position, false);
                mCheckStates.put(position, newValue);
                if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                    if (newValue) {
                        mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                    } else {
                        mCheckedIdStates.delete(mAdapter.getItemId(position));
                    }
                }
            } else {
                boolean newValue = !mCheckStates.get(position, false);
                if (newValue) {
                    mCheckStates.clear();
                    mCheckStates.put(position, true);
                    if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                        mCheckedIdStates.clear();
                        mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                    }
                }
            }

            mDataChanged = true;
            rememberSyncState();
            requestLayout();
        }

        handled |= super.performItemClick(view, position, id);

        return handled;
    }

    public void setItemChecked(int position, boolean value) {
        if (mChoiceMode == CHOICE_MODE_NONE) {
            return;
        }

        if (mChoiceMode == CHOICE_MODE_MULTIPLE) {
            mCheckStates.put(position, value);
            if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                if (value) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                } else {
                    mCheckedIdStates.delete(mAdapter.getItemId(position));
                }
            }
        } else {
            boolean updateIds = mCheckedIdStates != null && mAdapter.hasStableIds();
            // Clear all values if we're checking something, or unchecking the currently
            // selected item
            if (value || isItemChecked(position)) {
                mCheckStates.clear();
                if (updateIds) {
                    mCheckedIdStates.clear();
                }
            }
            // this may end up selecting the value we just cleared but this way
            // we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
            if (value) {
                mCheckStates.put(position, true);
                if (updateIds) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                }
            }
        }

        // Do not generate a data change while we are in the layout phase
        if (!mInLayout && !mBlockLayoutRequests) {
            mDataChanged = true;
            rememberSyncState();
            requestLayout();
        }
    }

    public boolean isItemChecked(int position) {
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            return mCheckStates.get(position);
        }

        return false;
    }

    public int getCheckedItemPosition() {
        if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
            return mCheckStates.keyAt(0);
        }

        return INVALID_POSITION;
    }

    public SparseBooleanArray getCheckedItemPositions() {
        if (mChoiceMode != CHOICE_MODE_NONE) {
            return mCheckStates;
        }
        return null;
    }

    @Deprecated
    public long[] getCheckItemIds() {
        // Use new behavior that correctly handles stable ID mapping.
        if (mAdapter != null && mAdapter.hasStableIds()) {
            return getCheckedItemIds();
        }

        // Old behavior was buggy, but would sort of work for adapters without stable IDs.
        // Fall back to it to support legacy apps.
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null && mAdapter != null) {
            final SparseBooleanArray states = mCheckStates;
            final int count = states.size();
            final long[] ids = new long[count];
            final ListAdapter adapter = mAdapter;

            int checkedCount = 0;
            for (int i = 0; i < count; i++) {
                if (states.valueAt(i)) {
                    ids[checkedCount++] = adapter.getItemId(states.keyAt(i));
                }
            }

            // Trim array if needed. mCheckStates may contain false values
            // resulting in checkedCount being smaller than count.
            if (checkedCount == count) {
                return ids;
            } else {
                final long[] result = new long[checkedCount];
                System.arraycopy(ids, 0, result, 0, checkedCount);

                return result;
            }
        }
        return new long[0];
    }

    public long[] getCheckedItemIds() {
        if (mChoiceMode == CHOICE_MODE_NONE || mCheckedIdStates == null || mAdapter == null) {
            return new long[0];
        }

        final LongSparseArray<Boolean> idStates = mCheckedIdStates;
        final int count = idStates.size();
        final long[] ids = new long[count];

        for (int i = 0; i < count; i++) {
            ids[i] = idStates.keyAt(i);
        }

        return ids;
    }

    public void clearChoices() {
        if (mCheckStates != null) {
            mCheckStates.clear();
        }
        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
    }

    static class SavedState extends View.BaseSavedState {
        SparseBooleanArray checkState;
        LongSparseArray<Boolean> checkIdState;

        /**
         * Constructor called from {@link ListView#onSaveInstanceState()}
         */
        SavedState(Parcelable superState, SparseBooleanArray checkState,
                   LongSparseArray<Boolean> checkIdState) {
            super(superState);
            this.checkState = checkState;
            this.checkIdState = checkIdState;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            checkState = in.readSparseBooleanArray();
            long[] idState = in.createLongArray();

            if (idState.length > 0) {
                checkIdState = new LongSparseArray<>();
                for (long id : idState) {
                    checkIdState.put(id, Boolean.TRUE);
                }
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeSparseBooleanArray(checkState);
            long[] keys = new long[checkIdState != null ? checkIdState.size() : 0];
            if (checkIdState != null) {
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = checkIdState.keyAt(i);
                }
            }
            out.writeLongArray(checkIdState != null ? keys : new long[0]);
        }

        @Override
        public String toString() {
            return "ListView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " checkState=" + checkState + "}";
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
        return new SavedState(superState, mCheckStates, mCheckedIdStates);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;

        super.onRestoreInstanceState(ss.getSuperState());

        if (ss.checkState != null) {
            mCheckStates = ss.checkState;
        }

        if (ss.checkIdState != null) {
            mCheckedIdStates = ss.checkIdState;
        }
    }

}
