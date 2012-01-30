package cz.fhejl.pubtran;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

public class MyAutoComplete extends EditText implements Filter.FilterListener {
	public long lastDismissTime = 0;
	public Activity activity;
	public MyPopupWindow mPopup;

	static final boolean DEBUG = false;
	static final String TAG = "AutoCompleteTextView";

	private static final int HINT_VIEW_ID = 0x17;

	private ListAdapter mAdapter;
	private Filter mFilter;
	private int mThreshold = 0;

	private DropDownListView mDropDownList;
	private int mDropDownVerticalOffset;
	private int mDropDownHorizontalOffset;
	private int mDropDownAnchorId;
	private View mDropDownAnchorView; // view is retrieved lazily from id once
	// needed
	private int mDropDownWidth;

	private Drawable mDropDownListHighlight;

	private AdapterView.OnItemClickListener mItemClickListener;
	private AdapterView.OnItemSelectedListener mItemSelectedListener;

	private final DropDownItemClickListener mDropDownItemClickListener = new DropDownItemClickListener();

	private int mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;
	private boolean mOpenBefore;

	private Validator mValidator = null;

	private boolean mBlockCompletion;

	private MyAutoComplete.ListSelectorHider mHideSelector;

	// Indicates whether this AutoCompleteTextView is attached to a window or
	// not
	// The widget is attached to a window when mAttachCount > 0
	private int mAttachCount;

	public MyAutoComplete(Context context) {
		this(context, null);
	}

	public MyAutoComplete(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.autoCompleteTextViewStyle);
	}

	public MyAutoComplete(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mPopup = new MyPopupWindow(context, attrs, android.R.attr.autoCompleteTextViewStyle);
		mPopup.setOnDismissListener(new MyPopupWindow.OnDismissListener() {

			@Override
			public void onDismiss() {
				lastDismissTime = SystemClock.uptimeMillis();
			}
		});

		mDropDownListHighlight = getResources().getDrawable(android.R.drawable.list_selector_background);
		mDropDownVerticalOffset = (int) (-6f * getResources().getDisplayMetrics().density + 0.5f);
		mDropDownHorizontalOffset = 0;

		// Get the anchor's id now, but the view won't be ready, so wait to
		// actually get the
		// view and store it in mDropDownAnchorView lazily in
		// getDropDownAnchorView later.
		// Defaults to NO_ID, in which case the getDropDownAnchorView method
		// will simply return
		// this TextView, as a default anchoring point.
		mDropDownAnchorId = View.NO_ID;

		// For dropdown width, the developer can specify a specific width, or
		// FILL_PARENT
		// (for full screen width) or WRAP_CONTENT (to match the width of the
		// anchored view).
		mDropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT;

		// Always turn on the auto complete input type flag, since it
		// makes no sense to use this widget without it.
		int inputType = getInputType();
		if ((inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			inputType |= EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE;
			setRawInputType(inputType);
		}

		setFocusable(true);

		addTextChangedListener(new MyWatcher());
	}

	/**
	 * Sets this to be single line; a separate method so
	 * MultiAutoCompleteTextView can skip this.
	 */
	/* package */void finishInit() {
		setSingleLine();
	}

	/**
	 * <p>
	 * Sets the optional hint text that is displayed at the bottom of the the
	 * matching list. This can be used as a cue to the user on how to best use
	 * the list, or to provide extra information.
	 * </p>
	 * 
	 * @param hint
	 *            the text to be displayed to the user
	 * 
	 * @attr ref android.R.styleable#AutoCompleteTextView_completionHint
	 */
	public void setCompletionHint(CharSequence hint) {

	}

	/**
	 * <p>
	 * Returns the current width for the auto-complete drop down list. This can
	 * be a fixed width, or {@link ViewGroup.LayoutParams#FILL_PARENT} to fill
	 * the screen, or {@link ViewGroup.LayoutParams#WRAP_CONTENT} to fit the
	 * width of its anchor view.
	 * </p>
	 * 
	 * @return the width for the drop down list
	 */
	public int getDropDownWidth() {
		return mDropDownWidth;
	}

	/**
	 * <p>
	 * Sets the current width for the auto-complete drop down list. This can be
	 * a fixed width, or {@link ViewGroup.LayoutParams#FILL_PARENT} to fill the
	 * screen, or {@link ViewGroup.LayoutParams#WRAP_CONTENT} to fit the width
	 * of its anchor view.
	 * </p>
	 * 
	 * @param width
	 *            the width to use
	 */
	public void setDropDownWidth(int width) {
		mDropDownWidth = width;
	}

	/**
	 * <p>
	 * Returns the id for the view that the auto-complete drop down list is
	 * anchored to.
	 * </p>
	 * 
	 * @return the view's id, or {@link View#NO_ID} if none specified
	 */
	public int getDropDownAnchor() {
		return mDropDownAnchorId;
	}

	/**
	 * <p>
	 * Sets the view to which the auto-complete drop down list should anchor.
	 * The view corresponding to this id will not be loaded until the next time
	 * it is needed to avoid loading a view which is not yet instantiated.
	 * </p>
	 * 
	 * @param id
	 *            the id to anchor the drop down list view to
	 */
	public void setDropDownAnchor(int id) {
		mDropDownAnchorId = id;
		mDropDownAnchorView = null;
	}

	/**
	 * <p>
	 * Returns the number of characters the user must type before the drop down
	 * list is shown.
	 * </p>
	 * 
	 * @return the minimum number of characters to type to show the drop down
	 * 
	 * @see #setThreshold(int)
	 */
	public int getThreshold() {
		return mThreshold;
	}

	/**
	 * <p>
	 * Specifies the minimum number of characters the user has to type in the
	 * edit box before the drop down list is shown.
	 * </p>
	 * 
	 * <p>
	 * When <code>threshold</code> is less than or equals 0, a threshold of 1 is
	 * applied.
	 * </p>
	 * 
	 * @param threshold
	 *            the number of characters to type before the drop down is shown
	 * 
	 * @see #getThreshold()
	 * 
	 * @attr ref android.R.styleable#AutoCompleteTextView_completionThreshold
	 */
	public void setThreshold(int threshold) {
		mThreshold = threshold;
	}

	/**
	 * <p>
	 * Sets the listener that will be notified when the user clicks an item in
	 * the drop down list.
	 * </p>
	 * 
	 * @param l
	 *            the item click listener
	 */
	public void setOnItemClickListener(AdapterView.OnItemClickListener l) {
		mItemClickListener = l;
	}

	/**
	 * <p>
	 * Sets the listener that will be notified when the user selects an item in
	 * the drop down list.
	 * </p>
	 * 
	 * @param l
	 *            the item selected listener
	 */
	public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener l) {
		mItemSelectedListener = l;
	}

	/**
	 * <p>
	 * Returns the listener that is notified whenever the user clicks an item in
	 * the drop down list.
	 * </p>
	 * 
	 * @return the item click listener
	 * 
	 * @deprecated Use {@link #getOnItemClickListener()} intead
	 */
	@Deprecated
	public AdapterView.OnItemClickListener getItemClickListener() {
		return mItemClickListener;
	}

	/**
	 * <p>
	 * Returns the listener that is notified whenever the user selects an item
	 * in the drop down list.
	 * </p>
	 * 
	 * @return the item selected listener
	 * 
	 * @deprecated Use {@link #getOnItemSelectedListener()} intead
	 */
	@Deprecated
	public AdapterView.OnItemSelectedListener getItemSelectedListener() {
		return mItemSelectedListener;
	}

	/**
	 * <p>
	 * Returns the listener that is notified whenever the user clicks an item in
	 * the drop down list.
	 * </p>
	 * 
	 * @return the item click listener
	 */
	public AdapterView.OnItemClickListener getOnItemClickListener() {
		return mItemClickListener;
	}

	/**
	 * <p>
	 * Returns the listener that is notified whenever the user selects an item
	 * in the drop down list.
	 * </p>
	 * 
	 * @return the item selected listener
	 */
	public AdapterView.OnItemSelectedListener getOnItemSelectedListener() {
		return mItemSelectedListener;
	}

	/**
	 * <p>
	 * Returns a filterable list adapter used for auto completion.
	 * </p>
	 * 
	 * @return a data adapter used for auto completion
	 */
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	/**
	 * <p>
	 * Changes the list of data used for auto completion. The provided list must
	 * be a filterable list adapter.
	 * </p>
	 * 
	 * <p>
	 * The caller is still responsible for managing any resources used by the
	 * adapter. Notably, when the AutoCompleteTextView is closed or released,
	 * the adapter is not notified. A common case is the use of
	 * {@link android.widget.CursorAdapter}, which contains a
	 * {@link android.database.Cursor} that must be closed. This can be done
	 * automatically (see
	 * {@link android.app.Activity#startManagingCursor(android.database.Cursor)
	 * startManagingCursor()}), or by manually closing the cursor when the
	 * AutoCompleteTextView is dismissed.
	 * </p>
	 * 
	 * @param adapter
	 *            the adapter holding the auto completion data
	 * 
	 * @see #getAdapter()
	 * @see android.widget.Filterable
	 * @see android.widget.ListAdapter
	 */
	public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
		mAdapter = adapter;
		if (mAdapter != null) {
			// noinspection unchecked
			mFilter = ((Filterable) mAdapter).getFilter();
		} else {
			mFilter = null;
		}

		if (mDropDownList != null) {
			mDropDownList.setAdapter(mAdapter);
		}
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (isPopupShowing()) {
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				dismissDropDown();
				InputMethodManager imm = (InputMethodManager) activity
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(getWindowToken(), 0);
				return true;
			}
		}
		return super.onKeyPreIme(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (isPopupShowing() && mDropDownList.getSelectedItemPosition() >= 0) {
			boolean consumed = mDropDownList.onKeyUp(keyCode, event);
			if (consumed) {
				switch (keyCode) {
				// if the list accepts the key events and the key event
				// was a click, the text view gets the selected item
				// from the drop down as its content
				case KeyEvent.KEYCODE_ENTER:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					performCompletion();
					return true;
				}
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// when the drop down is shown, we drive it directly
		if (isPopupShowing()) {
			// the key events are forwarded to the list in the drop down view
			// note that ListView handles space but we don't want that to happen
			// also if selection is not currently in the drop down, then don't
			// let center or enter presses go there since that would cause it
			// to select one of its items
			if (keyCode != KeyEvent.KEYCODE_SPACE
					&& (mDropDownList.getSelectedItemPosition() >= 0 || (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_DPAD_CENTER))) {
				int curIndex = mDropDownList.getSelectedItemPosition();
				boolean consumed;
				final boolean below = !mPopup.isAboveAnchor();
				if ((below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex <= 0)
						|| (!below && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && curIndex >= mDropDownList
								.getAdapter().getCount() - 1)) {
					// When the selection is at the top, we block the key
					// event to prevent focus from moving.
					// mDropDownList.hideSelector();
					mDropDownList.requestLayout();
					mPopup.setInputMethodMode(MyPopupWindow.INPUT_METHOD_NEEDED);
					mPopup.update();
					return true;
				}
				consumed = mDropDownList.onKeyDown(keyCode, event);
				if (DEBUG)
					Log.v(TAG, "Key down: code=" + keyCode + " list consumed=" + consumed);
				if (consumed) {
					// If it handled the key event, then the user is
					// navigating in the list, so we should put it in front.
					mPopup.setInputMethodMode(MyPopupWindow.INPUT_METHOD_NOT_NEEDED);
					// Here's a little trick we need to do to make sure that
					// the list view is actually showing its focus indicator,
					// by ensuring it has focus and getting its window out
					// of touch mode.
					mDropDownList.requestFocusFromTouch();
					mPopup.update();

					switch (keyCode) {
					// avoid passing the focus from the text view to the
					// next component
					case KeyEvent.KEYCODE_ENTER:
					case KeyEvent.KEYCODE_DPAD_CENTER:
					case KeyEvent.KEYCODE_DPAD_DOWN:
					case KeyEvent.KEYCODE_DPAD_UP:
						return true;
					}
				} else {
					if (below && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
						// when the selection is at the bottom, we block the
						// event to avoid going to the next focusable widget
						Adapter adapter = mDropDownList.getAdapter();
						if (adapter != null && curIndex == adapter.getCount() - 1) {
							return true;
						}
					} else if (!below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex == 0) {
						return true;
					}
				}
			}
		} else {
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				performValidation();
			}
		}

		mLastKeyCode = keyCode;
		boolean handled = super.onKeyDown(keyCode, event);
		mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;

		if (handled && isPopupShowing() && mDropDownList != null) {
			clearListSelection();
		}

		return handled;
	}

	/**
	 * Returns <code>true</code> if the amount of text in the field meets or
	 * exceeds the {@link #getThreshold} requirement. You can override this to
	 * impose a different standard for when filtering will be triggered.
	 */
	public boolean enoughToFilter() {
		return getText().length() >= mThreshold;
	}

	/**
	 * This is used to watch for edits to the text view. Note that we call to
	 * methods on the auto complete text view class so that we can access
	 * private vars without going through thunks.
	 */
	private class MyWatcher implements TextWatcher {
		public void afterTextChanged(Editable s) {
			doAfterTextChanged();
		}

		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			doBeforeTextChanged();
		}

		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	}

	void doBeforeTextChanged() {
		if (mBlockCompletion)
			return;

		// when text is changed, inserted or deleted, we attempt to show
		// the drop down
		mOpenBefore = isPopupShowing();
		if (DEBUG)
			Log.v(TAG, "before text changed: open=" + mOpenBefore);
	}

	void doAfterTextChanged() {
		if (mBlockCompletion)
			return;

		// if the list was open before the keystroke, but closed afterwards,
		// then something in the keystroke processing (an input filter perhaps)
		// called performCompletion() and we shouldn't do any more processing.
		if (DEBUG)
			Log.v(TAG, "after text changed: openBefore=" + mOpenBefore + " open=" + isPopupShowing());
		if (mOpenBefore && !isPopupShowing()) {
			return;
		}

		// the drop down is shown only when a minimum number of characters
		// was typed in the text view
		if (enoughToFilter()) {
			if (mFilter != null) {
				performFiltering(getText(), mLastKeyCode);
			}
		} else {
			// drop down is automatically dismissed when enough characters
			// are deleted from the text view
			dismissDropDown();
			if (mFilter != null) {
				mFilter.filter(null);
			}
		}
	}

	/**
	 * <p>
	 * Indicates whether the popup menu is showing.
	 * </p>
	 * 
	 * @return true if the popup menu is showing, false otherwise
	 */
	public boolean isPopupShowing() {
		return mPopup.isShowing();
	}

	/**
	 * <p>
	 * Converts the selected item from the drop down list into a sequence of
	 * character that can be used in the edit box.
	 * </p>
	 * 
	 * @param selectedItem
	 *            the item selected by the user for completion
	 * 
	 * @return a sequence of characters representing the selected suggestion
	 */
	protected CharSequence convertSelectionToString(Object selectedItem) {
		return mFilter.convertResultToString(selectedItem);
	}

	/**
	 * <p>
	 * Clear the list selection. This may only be temporary, as user input will
	 * often bring it back.
	 */
	public void clearListSelection() {
		if (mDropDownList != null) {
			// mDropDownList.hideSelector();
			mDropDownList.requestLayout();
		}
	}

	/**
	 * Set the position of the dropdown view selection.
	 * 
	 * @param position
	 *            The position to move the selector to.
	 */
	public void setListSelection(int position) {
		if (mPopup.isShowing() && (mDropDownList != null)) {
			mDropDownList.setSelection(position);
			// ListView.setSelection() will call requestLayout()
		}
	}

	/**
	 * Get the position of the dropdown view selection, if there is one. Returns
	 * {@link ListView#INVALID_POSITION ListView.INVALID_POSITION} if there is
	 * no dropdown or if there is no selection.
	 * 
	 * @return the position of the current selection, if there is one, or
	 *         {@link ListView#INVALID_POSITION ListView.INVALID_POSITION} if
	 *         not.
	 * 
	 * @see ListView#getSelectedItemPosition()
	 */
	public int getListSelection() {
		if (mPopup.isShowing() && (mDropDownList != null)) {
			return mDropDownList.getSelectedItemPosition();
		}
		return ListView.INVALID_POSITION;
	}

	/**
	 * <p>
	 * Starts filtering the content of the drop down list. The filtering pattern
	 * is the content of the edit box. Subclasses should override this method to
	 * filter with a different pattern, for instance a substring of
	 * <code>text</code>.
	 * </p>
	 * 
	 * @param text
	 *            the filtering pattern
	 * @param keyCode
	 *            the last character inserted in the edit box; beware that this
	 *            will be null when text is being added through a soft input
	 *            method.
	 */
	protected void performFiltering(CharSequence text, int keyCode) {
		mFilter.filter(text, this);
	}

	/**
	 * <p>
	 * Performs the text completion by converting the selected item from the
	 * drop down list into a string, replacing the text box's content with this
	 * string and finally dismissing the drop down menu.
	 * </p>
	 */
	public void performCompletion() {
		performCompletion(null, -1, -1);
	}

	@Override
	public void onCommitCompletion(CompletionInfo completion) {
		if (isPopupShowing()) {
			mBlockCompletion = true;
			replaceText(completion.getText());
			mBlockCompletion = false;

			if (mItemClickListener != null) {
				final DropDownListView list = mDropDownList;
				// Note that we don't have a View here, so we will need to
				// supply null. Hopefully no existing apps crash...
				mItemClickListener.onItemClick(list, null, completion.getPosition(), completion.getId());
			}
		}
	}

	private void performCompletion(View selectedView, int position, long id) {
		if (isPopupShowing()) {
			Object selectedItem;
			if (position < 0) {
				selectedItem = mDropDownList.getSelectedItem();
			} else {
				selectedItem = mAdapter.getItem(position);
			}
			if (selectedItem == null) {
				Log.w(TAG, "performCompletion: no selected item");
				return;
			}

			mBlockCompletion = true;
			replaceText(convertSelectionToString(selectedItem));
			mBlockCompletion = false;

			if (mItemClickListener != null) {
				final DropDownListView list = mDropDownList;

				if (selectedView == null || position < 0) {
					selectedView = list.getSelectedView();
					position = list.getSelectedItemPosition();
					id = list.getSelectedItemId();
				}
				mItemClickListener.onItemClick(list, selectedView, position, id);
			}
		}

		dismissDropDown();
	}

	/**
	 * Identifies whether the view is currently performing a text completion, so
	 * subclasses can decide whether to respond to text changed events.
	 */
	public boolean isPerformingCompletion() {
		return mBlockCompletion;
	}

	/**
	 * <p>
	 * Performs the text completion by replacing the current text by the
	 * selected item. Subclasses should override this method to avoid replacing
	 * the whole content of the edit box.
	 * </p>
	 * 
	 * @param text
	 *            the selected suggestion in the drop down list
	 */
	protected void replaceText(CharSequence text) {
		setText(text);
		// make sure we keep the caret at the end of the text view
		Editable spannable = getText();
		Selection.setSelection(spannable, spannable.length());
	}

	public void onFilterComplete(int count) {
		if (mAttachCount <= 0)
			return;

		/*
		 * This checks enoughToFilter() again because filtering requests are
		 * asynchronous, so the result may come back after enough text has since
		 * been deleted to make it no longer appropriate to filter.
		 */

		if (count > 0 && enoughToFilter()) {
			if (hasFocus() && hasWindowFocus()) {
				showDropDown();
			}
		} else {
			dismissDropDown();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		performValidation();
		if (!hasWindowFocus) {
			dismissDropDown();
		}
	}

	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(focused, direction, previouslyFocusedRect);
		performValidation();
		if (!focused) {
			dismissDropDown();
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mAttachCount++;
	}

	@Override
	protected void onDetachedFromWindow() {
		dismissDropDown();
		mAttachCount--;
		super.onDetachedFromWindow();
	}

	/**
	 * <p>
	 * Closes the drop down if present on screen.
	 * </p>
	 */
	public void dismissDropDown() {
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.displayCompletions(this, null);
		}
		mPopup.dismiss();
		mPopup.setContentView(null);
		mDropDownList = null;
	}

	@Override
	protected boolean setFrame(int l, int t, int r, int b) {
		boolean result = super.setFrame(l, t, r, b);

		if (mPopup.isShowing()) {
			mPopup.update(this, r - l, -1);
		}

		return result;
	}

	/**
	 * Set the horizontal offset with respect to {@link #setDropDownAnchor(int)}
	 * 
	 * @hide pending API council review
	 */
	public void setDropDownHorizontalOffset(int horizontalOffset) {
		mDropDownHorizontalOffset = horizontalOffset;
	}

	/**
	 * Set the vertical offset with respect to {@link #setDropDownAnchor(int)}
	 * 
	 * @hide pending API council review
	 */
	public void setDropDownVerticalOffset(int verticalOffset) {
		mDropDownVerticalOffset = verticalOffset;
	}

	/**
	 * <p>
	 * Used for lazy instantiation of the anchor view from the id we have. If
	 * the value of the id is NO_ID or we can't find a view for the given id, we
	 * return this TextView as the default anchoring point.
	 * </p>
	 */
	private View getDropDownAnchorView() {
		if (mDropDownAnchorView == null && mDropDownAnchorId != View.NO_ID) {
			mDropDownAnchorView = getRootView().findViewById(mDropDownAnchorId);
		}
		return mDropDownAnchorView == null ? this : mDropDownAnchorView;
	}

	/**
	 * <p>
	 * Displays the drop down on screen.
	 * </p>
	 */
	public void showDropDown() {
		int height = buildDropDown();
		if (mPopup.isShowing()) {
			int widthSpec;
			if (mDropDownWidth == ViewGroup.LayoutParams.FILL_PARENT) {
				// The call to PopupWindow's update method below can accept -1
				// for any
				// value you do not want to update.
				widthSpec = -1;
			} else if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
				widthSpec = getDropDownAnchorView().getWidth();
			} else {
				widthSpec = mDropDownWidth;
			}
			mPopup.update(getDropDownAnchorView(), mDropDownHorizontalOffset, mDropDownVerticalOffset,
					widthSpec, height);
		} else {
			if (mDropDownWidth == ViewGroup.LayoutParams.FILL_PARENT) {
				mPopup.setWindowLayoutMode(ViewGroup.LayoutParams.FILL_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
			} else {
				mPopup.setWindowLayoutMode(0, ViewGroup.LayoutParams.WRAP_CONTENT);
				if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
					mPopup.setWidth(getDropDownAnchorView().getWidth());
				} else {
					mPopup.setWidth(mDropDownWidth);
				}
			}
			mPopup.setHeight(height);
			mPopup.setInputMethodMode(MyPopupWindow.INPUT_METHOD_NEEDED);
			mPopup.setOutsideTouchable(true);
			mPopup.setTouchInterceptor(new PopupTouchIntercepter());
			mPopup
					.showAsDropDown(getDropDownAnchorView(), mDropDownHorizontalOffset,
							mDropDownVerticalOffset);
			mDropDownList.setSelection(ListView.INVALID_POSITION);
			// mDropDownList.hideSelector();
			mDropDownList.requestFocus();
			post(mHideSelector);
		}
	}

	/**
	 * <p>
	 * Builds the popup window's content and returns the height the popup should
	 * have. Returns -1 when the content already exists.
	 * </p>
	 * 
	 * @return the content's height or -1 if content already exists
	 */
	private int buildDropDown() {
		ViewGroup dropDownView;
		int otherHeights = 0;

		if (mAdapter != null) {
			InputMethodManager imm = (InputMethodManager) activity
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				int N = mAdapter.getCount();
				if (N > 20)
					N = 20;
				CompletionInfo[] completions = new CompletionInfo[N];
				for (int i = 0; i < N; i++) {
					Object item = mAdapter.getItem(i);
					long id = mAdapter.getItemId(i);
					completions[i] = new CompletionInfo(id, i, convertSelectionToString(item));
				}
				imm.displayCompletions(this, completions);
			}
		}

		if (mDropDownList == null) {
			Context context = getContext();

			mHideSelector = new ListSelectorHider();

			mDropDownList = new DropDownListView(context);
			mDropDownList.setSelector(mDropDownListHighlight);
			mDropDownList.setAdapter(mAdapter);
			mDropDownList.setVerticalFadingEdgeEnabled(true);
			mDropDownList.setOnItemClickListener(mDropDownItemClickListener);
			mDropDownList.setFocusable(true);
			mDropDownList.setFocusableInTouchMode(true);

			if (mItemSelectedListener != null) {
				mDropDownList.setOnItemSelectedListener(mItemSelectedListener);
			}

			dropDownView = mDropDownList;

			View hintView = getHintView(context);
			if (hintView != null) {
				// if an hint has been specified, we accomodate more space for
				// it and
				// add a text view in the drop down menu, at the bottom of the
				// list
				LinearLayout hintContainer = new LinearLayout(context);
				hintContainer.setOrientation(LinearLayout.VERTICAL);

				LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.FILL_PARENT, 0, 1.0f);
				hintContainer.addView(dropDownView, hintParams);
				hintContainer.addView(hintView);

				// measure the hint's height to find how much more vertical
				// space
				// we need to add to the drop down's height
				int widthSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST);
				int heightSpec = MeasureSpec.UNSPECIFIED;
				hintView.measure(widthSpec, heightSpec);

				hintParams = (LinearLayout.LayoutParams) hintView.getLayoutParams();
				otherHeights = hintView.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;

				dropDownView = hintContainer;
			}

			mPopup.setContentView(dropDownView);
		} else {
			dropDownView = (ViewGroup) mPopup.getContentView();
			final View view = dropDownView.findViewById(HINT_VIEW_ID);
			if (view != null) {
				LinearLayout.LayoutParams hintParams = (LinearLayout.LayoutParams) view.getLayoutParams();
				otherHeights = view.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;
			}
		}

		// Max height available on the screen for a popup anchored to us
		final int maxHeight = mPopup.getMaxAvailableHeight(this, mDropDownVerticalOffset);
		// otherHeights += dropDownView.getPaddingTop() +
		// dropDownView.getPaddingBottom();

		return mDropDownList.measureHeightOfChildren2(MeasureSpec.UNSPECIFIED, 0, -1, maxHeight
				- otherHeights, 2)
				+ otherHeights;
	}

	private View getHintView(Context context) {
		return null;
	}

	/**
	 * Sets the validator used to perform text validation.
	 * 
	 * @param validator
	 *            The validator used to validate the text entered in this
	 *            widget.
	 * 
	 * @see #getValidator()
	 * @see #performValidation()
	 */
	public void setValidator(Validator validator) {
		mValidator = validator;
	}

	/**
	 * Returns the Validator set with {@link #setValidator}, or
	 * <code>null</code> if it was not set.
	 * 
	 * @see #setValidator(android.widget.AutoCompleteTextView.Validator)
	 * @see #performValidation()
	 */
	public Validator getValidator() {
		return mValidator;
	}

	/**
	 * If a validator was set on this view and the current string is not valid,
	 * ask the validator to fix it.
	 * 
	 * @see #getValidator()
	 * @see #setValidator(android.widget.AutoCompleteTextView.Validator)
	 */
	public void performValidation() {
		if (mValidator == null)
			return;

		CharSequence text = getText();

		if (!TextUtils.isEmpty(text) && !mValidator.isValid(text)) {
			setText(mValidator.fixText(text));
		}
	}
	
	public void setTextNoDropdown(CharSequence text) {
		setThreshold(1000);
		setText(text);
		setThreshold(0);
	}

	/**
	 * Returns the Filter obtained from {@link Filterable#getFilter}, or
	 * <code>null</code> if {@link #setAdapter} was not called with a
	 * Filterable.
	 */
	protected Filter getFilter() {
		return mFilter;
	}

	private class ListSelectorHider implements Runnable {
		public void run() {
			if (mDropDownList != null) {
				// mDropDownList.hideSelector();
				mDropDownList.requestLayout();
			}
		}
	}

	private class PopupTouchIntercepter implements OnTouchListener {
		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				mPopup.setInputMethodMode(MyPopupWindow.INPUT_METHOD_NOT_NEEDED);
				mPopup.update();
			}
			return false;
		}
	}

	private class DropDownItemClickListener implements AdapterView.OnItemClickListener {
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			performCompletion(v, position, id);
		}
	}

	/**
	 * <p>
	 * Wrapper class for a ListView. This wrapper hijacks the focus to make sure
	 * the list uses the appropriate drawables and states when displayed on
	 * screen within a drop down. The focus is never actually passed to the drop
	 * down; the list only looks focused.
	 * </p>
	 */
	private static class DropDownListView extends ListView {
		/**
		 * <p>
		 * Creates a new list view wrapper.
		 * </p>
		 * 
		 * @param context
		 *            this view's context
		 */
		public DropDownListView(Context context) {
			super(context, null, android.R.attr.dropDownListViewStyle);
		}

		/**
		 * <p>
		 * Avoids jarring scrolling effect by ensuring that list elements made
		 * of a text view fit on a single line.
		 * </p>
		 * 
		 * @param position
		 *            the item index in the list to get a view for
		 * @return the view for the specified item
		 */
		// @Override
		// protected View obtainView(int position) {
		// View view = super.obtainView(position);
		//
		// if (view instanceof TextView) {
		// ((TextView) view).setHorizontallyScrolling(true);
		// }
		//
		// return view;
		// }

		/**
		 * <p>
		 * Returns the top padding of the currently selected view.
		 * </p>
		 * 
		 * @return the height of the top padding for the selection
		 */
		@SuppressWarnings("unused")
		public int getSelectionPaddingTop() {
			// return mSelectionTopPadding;
			return 0;
		}

		/**
		 * <p>
		 * Returns the bottom padding of the currently selected view.
		 * </p>
		 * 
		 * @return the height of the bottom padding for the selection
		 */
		@SuppressWarnings("unused")
		public int getSelectionPaddingBottom() {
			// return mSelectionBottomPadding;
			return 0;
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always
		 */
		@Override
		public boolean hasWindowFocus() {
			return true;
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always
		 */
		@Override
		public boolean isFocused() {
			return true;
		}

		/**
		 * <p>
		 * Returns the focus state in the drop down.
		 * </p>
		 * 
		 * @return true always
		 */
		@Override
		public boolean hasFocus() {
			return true;
		}

		final int measureHeightOfChildren2(int widthMeasureSpec, int startPosition, int endPosition,
				final int maxHeight, int disallowPartialChildPosition) {

			endPosition = (endPosition == -1) ? getAdapter().getCount() - 1 : endPosition;
			int returnedHeight = 0;
			final int dividerHeight = 1;
			float scale = getResources().getDisplayMetrics().density;

			for (int i = startPosition; i <= endPosition; ++i) {
				if (i > 0) {
					// Count the divider for all but one child
					returnedHeight += dividerHeight;
				}

				returnedHeight += (int) (41f * scale + 0.5f);

				if (returnedHeight >= maxHeight)
					return maxHeight;
			}

			// At this point, we went through the range of children, and they
			// each
			// completely fit, so return the returnedHeight
			return returnedHeight;
		}

		protected int[] onCreateDrawableState(int extraSpace) {
			int[] res = super.onCreateDrawableState(extraSpace);
			return res;
		}
	}

	/**
	 * This interface is used to make sure that the text entered in this
	 * TextView complies to a certain format. Since there is no foolproof way to
	 * prevent the user from leaving this View with an incorrect value in it,
	 * all we can do is try to fix it ourselves when this happens.
	 */
	public interface Validator {
		/**
		 * Validates the specified text.
		 * 
		 * @return true If the text currently in the text editor is valid.
		 * 
		 * @see #fixText(CharSequence)
		 */
		boolean isValid(CharSequence text);

		/**
		 * Corrects the specified text to make it valid.
		 * 
		 * @param invalidText
		 *            A string that doesn't pass validation:
		 *            isValid(invalidText) returns false
		 * 
		 * @return A string based on invalidText such as invoking isValid() on
		 *         it returns true.
		 * 
		 * @see #isValid(CharSequence)
		 */
		CharSequence fixText(CharSequence invalidText);
	}

}