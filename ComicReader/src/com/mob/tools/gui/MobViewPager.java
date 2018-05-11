package com.mob.tools.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/** 一个用于模仿ViewPager的控件 */
public class MobViewPager extends ViewGroup {
	private final static int TOUCH_STATE_REST = 0;
	private final static int TOUCH_STATE_SCROLLING = 1;
	private static final int SNAP_VELOCITY = 500;
	private static final int DECELERATION = 10;
	
	private int currentScreen;
	private int screenCount;
	private View previousPage;
	private View currentPage;
	private View nextPage;
	private Scroller scroller;
	private int touchSlop;
	private int maximumVelocity;
	private ViewPagerAdapter adapter;
	private VelocityTracker velocityTracker;
	private int touchState;
	private float lastMotionX;
	private float lastMotionY;
	private boolean skipScreen;//是否直接跳转到当前screen，不执行过多的getView方法
	private int flingVelocity;
	private int pageWidth;
	
	public MobViewPager(Context context) {
		this(context, null);
	}
	
	public MobViewPager(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public MobViewPager(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}
	
	private void init(Context context) {
		scroller = new Scroller(context, new Interpolator() {
			public float getInterpolation(float input) {
				return (2 - input) * input;
			}
		});
		
		touchState = TOUCH_STATE_REST;
		ViewConfiguration configuration = ViewConfiguration.get(context);
		touchSlop = configuration.getScaledTouchSlop();
		maximumVelocity = configuration.getScaledMaximumFlingVelocity();
	}
	
	public void setAdapter(ViewPagerAdapter adapter) {
		if (this.adapter != null) {
			this.adapter.setMobViewPager(null);
		}
		this.adapter = adapter;
		if (this.adapter != null) {
			this.adapter.setMobViewPager(this);
		}
		
		if (adapter == null) {
			currentScreen = 0;
			removeAllViews();
			return;
		}
		
		screenCount = adapter.getCount();
		if (screenCount <= 0) {
			currentScreen = 0;
			removeAllViews();
			return;
		}
		
		if (screenCount <= currentScreen) {
			scrollToScreenOnUIThread(screenCount - 1, true);
		} else {
			removeAllViews();
			if (currentScreen > 0) {
				previousPage = adapter.getView(currentScreen - 1, previousPage, this);
				addView(previousPage);
			}
			currentPage = adapter.getView(currentScreen, currentPage, this);
			addView(currentPage);
			if (currentScreen < screenCount - 1) {
				nextPage = adapter.getView(currentScreen + 1, nextPage, this);
				addView(nextPage);
			}
		}
	}
	
	public int getCurrentScreen() {
		return currentScreen;
	}
	
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (adapter == null || screenCount <= 0) {
			return;
		}
		
		int width = getMeasuredWidth();
		int height = getMeasuredHeight();
		int adjustedWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
		int adjustedHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			child.measure(adjustedWidthMeasureSpec, adjustedHeightMeasureSpec);
		}
	}
	
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (adapter == null || screenCount <= 0) {
			return;
		}
		
		int width = r - l;
		int height = b - t;
		int cLeft = currentScreen * width;
		if (currentScreen > 0) {
			previousPage.layout(cLeft - width, 0, cLeft, height);
		}
		currentPage.layout(cLeft, 0, cLeft + width, height);
		if (currentScreen < screenCount - 1) {
			nextPage.layout(cLeft + width, 0, cLeft + width + width, height);
		}
		
		if (pageWidth != getWidth()) {
			int tmp = pageWidth;
			pageWidth = getWidth();
			if (tmp != 0) {
				adjustScroller();
			}
		}
	}
	
	private void adjustScroller() {
		skipScreen = true;
		if (currentPage != null && getFocusedChild() == currentPage) {
			currentPage.clearFocus();
		}
		int newX = currentScreen * getWidth();
		int delta = newX - getScrollX();
		scroller.abortAnimation();
		if (delta != 0) {
			scroller.startScroll(getScrollX(), 0, delta, 0, 0);
		}
		invalidate();
	}
	
	protected void dispatchDraw(Canvas canvas) {
		if (adapter == null || screenCount <= 0) {
			return;
		}
		
		long drawingTime = getDrawingTime();
		if (currentScreen > 0) {
			drawChild(canvas, previousPage, drawingTime);
		}
		drawChild(canvas, currentPage, drawingTime);
		if (currentScreen < screenCount - 1) {
			drawChild(canvas, nextPage, drawingTime);
		}
	}
	
	public void computeScroll() {
		if (adapter == null || screenCount <= 0) {
			return;
		}
		
		if (scroller.computeScrollOffset()) {
			scrollTo(scroller.getCurrX(), scroller.getCurrY());
			postInvalidate();
		} else {
			int lastScreen = currentScreen;
			int scrX = scroller.getCurrX();
			int w = getWidth();
			int index = scrX / w;
			if (scrX % w > w / 2) {
				index++;
			}
			currentScreen = Math.max(0, Math.min(index, screenCount - 1));
			if (lastScreen != currentScreen) {
				onScreenChange(lastScreen);
			}
		}
		if (adapter != null) {
			float position = ((float) getScrollX()) / getWidth();
			adapter.onScreenChanging(position);
		}
	}
	
	private void onScreenChange(int lastScreen) {
		if (adapter != null) {
			if (skipScreen && Math.abs(lastScreen - currentScreen) > 2) {
				//如果直接跳转，并且切换的屏幕在2个以外，则不去做过多的操作
				removeAllViews();
				if (currentScreen > 0) {
					previousPage = adapter.getView(currentScreen - 1, previousPage, this);
					addView(previousPage);
				}
				currentPage = adapter.getView(currentScreen, currentPage, this);
				addView(currentPage);
				if (currentScreen < screenCount - 1) {
					nextPage = adapter.getView(currentScreen + 1, nextPage, this);
					addView(nextPage);
				}
			} else {
				if (currentScreen > lastScreen) { // 从右往左
					for (int i = 0; i < currentScreen - lastScreen; i++) {
						int screen = lastScreen + i + 1;
						View tmp = previousPage;
						previousPage = currentPage;
						currentPage = nextPage;
						
						if (getChildCount() >= 3) {
							removeViewAt(0);
						}
						if (screen < screenCount - 1) {
							nextPage = adapter.getView(screen + 1, tmp, this);
							addView(nextPage);
						} else {
							nextPage = tmp;
						}
					}
				} else { // 从左往右
					for (int i = 0; i < lastScreen - currentScreen; i++) {
						int screen = lastScreen - i - 1;
						View tmp = nextPage;
						nextPage = currentPage;
						currentPage = previousPage;
						
						if (getChildCount() >= 3) {
							removeViewAt(2);
						}
						if (screen > 0) {
							previousPage = adapter.getView(screen - 1, tmp, this);
							addView(previousPage, 0);
						} else {
							previousPage = tmp;
						}
					}
				}
			}
			adapter.onScreenChange(currentScreen, lastScreen);
		}
	}
	
	public void scrollLeft(boolean immediate) {
		if (currentScreen > 0) {
			scrollToScreen(currentScreen - 1, immediate);
		}
	}
	
	public void scrollRight(boolean immediate) {
		if (currentScreen < screenCount - 1) {
			scrollToScreen(currentScreen + 1, immediate);
		}
	}
	
	public void scrollToScreen(final int whichScreen, final boolean immediate) {
		post(new Runnable() {
			public void run() {
				scrollToScreenOnUIThread(whichScreen, immediate);
			}
		});
	}

	@Deprecated
	public void scrollToScreen(int whichScreen, boolean immediate, boolean skip) {
		scrollToScreen(whichScreen, immediate);
	}
	
	private void scrollToScreenOnUIThread(int whichScreen, boolean immediate) {
		skipScreen = immediate;
		if (currentPage != null && getFocusedChild() == currentPage) {
			currentPage.clearFocus();
		}
		int newX = whichScreen * getWidth();
		int delta = newX - getScrollX();
		scroller.abortAnimation();
		if (delta != 0) {
			int duration = 0;
			if (!immediate) {
				int defDur = Math.abs(delta) / 2;
				if (flingVelocity != 0) {
					int v = Math.abs(flingVelocity);
					int s = Math.abs(delta);
					duration = (int) (1000 * (v - Math.sqrt(v * v - 2 * DECELERATION * s)) / DECELERATION);
				}
				if (duration == 0 || duration > defDur) {
					duration = defDur;
				}
			}
			scroller.startScroll(getScrollX(), 0, delta, 0, duration);
		}
		invalidate();
	}
	
	public boolean dispatchUnhandledMove(View focused, int direction) {
		if (adapter == null) {
			return super.dispatchUnhandledMove(focused, direction);
		}
		
		if (direction == View.FOCUS_LEFT) {
			if (currentScreen > 0) {
				scrollToScreenOnUIThread(currentScreen - 1, false);
				return true;
			}
		} else if (direction == View.FOCUS_RIGHT) {
			if (currentScreen < screenCount - 1) {
				scrollToScreenOnUIThread(currentScreen + 1, false);
				return true;
			}
		}
		return super.dispatchUnhandledMove(focused, direction);
	}
	
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		/*
		 * Shortcut the most recurring case: the user is in the dragging state
		 * and he is moving his finger. We want to intercept this motion.
		 */
		int action = ev.getAction();
		if ((action == MotionEvent.ACTION_MOVE) && (touchState != TOUCH_STATE_REST)) {
			return true;
		}
		
		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}
		velocityTracker.addMovement(ev);
		
		switch (action) {
			case MotionEvent.ACTION_MOVE: {
				/*
				 * Locally do absolute value. mLastMotionX is set to the y value of
				 * the down event.
				 */
				handleInterceptMove(ev);
			} break;
			case MotionEvent.ACTION_DOWN: {
				// Remember location of down touch
				float x1 = ev.getX();
				float y1 = ev.getY();
				lastMotionX = x1;
				lastMotionY = y1;
				
				/*
				 * If being flinged and user touches the screen, initiate drag;
				 * otherwise don't. mScroller.isFinished should be false when being
				 * flinged.
				 */
				touchState = scroller.isFinished() ? TOUCH_STATE_REST : TOUCH_STATE_SCROLLING;
			} break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
				touchState = TOUCH_STATE_REST;
			} break;
		}
		
		/*
		 * The only time we want to intercept motion events is if we are in the
		 * drag mode.
		 */
		return touchState != TOUCH_STATE_REST;
	}
	
	private void handleInterceptMove(MotionEvent ev) {
		float x = ev.getX();
		float y = ev.getY();
		int xDiff = (int) Math.abs(x - lastMotionX);
		int yDiff = (int) Math.abs(y - lastMotionY);
		
		if (yDiff < xDiff && xDiff > touchSlop) {
			touchState = TOUCH_STATE_SCROLLING;
			lastMotionX = x;
		}
	}
	
	public boolean onTouchEvent(MotionEvent ev) {
		if (adapter == null) {
			return false;
		}
		
		if (velocityTracker == null) {
			velocityTracker = VelocityTracker.obtain();
		}
		velocityTracker.addMovement(ev);
		
		int action = ev.getAction();
		float x = ev.getX();
		
		switch (action) {
			case MotionEvent.ACTION_DOWN: {
				// We can still get here even if we returned false from the
				// intercept function.
				// That's the only way we can get a TOUCH_STATE_REST (0) here.
				// That means that our child hasn't handled the event, so we need to
				// Log.d("ViewPagerClassic","caught a down touch event and touchstate =" +
				// touchState);
				if (touchState != TOUCH_STATE_REST) {
					/*
					 * If being flinged and user touches, stop the fling. isFinished
					 * will be false if being flinged.
					 */
					if (!scroller.isFinished()) {
						scroller.abortAnimation();
					}
					
					// Remember where the motion event started
					lastMotionX = x;
				}
			} break;
			case MotionEvent.ACTION_MOVE: {
				if (touchState == TOUCH_STATE_SCROLLING) {
					handleScrollMove(ev);
				} else {
					// NOTE: We will never hit this case in Android 2.2. This is to
					// fix a 2.1 bug.
					// We need to do the work of interceptTouchEvent here because we
					// don't intercept the move
					// on children who don't scroll.
					if (onInterceptTouchEvent(ev) && touchState == TOUCH_STATE_SCROLLING) {
						handleScrollMove(ev);
					}
				}
			} break;
			case MotionEvent.ACTION_UP: {
				if (touchState == TOUCH_STATE_SCROLLING) {
					velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
					flingVelocity = (int) velocityTracker.getXVelocity();
					
					if (flingVelocity > SNAP_VELOCITY && currentScreen > 0) {
						// Fling hard enough to move left
						scrollToScreenOnUIThread(currentScreen - 1, false);
					} else if (flingVelocity < -SNAP_VELOCITY && currentScreen < screenCount - 1) {
						// Fling hard enough to move right
						scrollToScreenOnUIThread(currentScreen + 1, false);
					} else {
						int screenWidth = getWidth();
						int whichScreen = (getScrollX() + (screenWidth / 2)) / screenWidth;
						scrollToScreenOnUIThread(whichScreen, false);
					}
					
					if (velocityTracker != null) {
						velocityTracker.recycle();
						velocityTracker = null;
					}
				}
				touchState = TOUCH_STATE_REST;
			} break;
			case MotionEvent.ACTION_CANCEL: {
				touchState = TOUCH_STATE_REST;
			} break;
		}
		
		return true;
	}
	
	private void handleScrollMove(MotionEvent ev) {
		if (adapter == null) {
			return;
		}
		
		// Scroll to follow the motion event
		float x1 = ev.getX();
		int deltaX = (int) (lastMotionX - x1);
		lastMotionX = x1;
		
		if (deltaX < 0) {
			if (getScrollX() > 0) {
				// Scrollby invalidates automatically
				scrollBy(Math.max(-getScrollX(), deltaX), 0);
			}
		} else if (deltaX > 0) {
			if (getChildCount() != 0) {
				View lastScr = getChildAt(getChildCount() - 1);
				int availableToScroll = lastScr.getRight() - getScrollX() - getWidth();
				if (availableToScroll > 0) {
					// Scrollby invalidates automatically
					scrollBy(Math.min(availableToScroll, deltaX), 0);
				}
			}
		}
	}

}