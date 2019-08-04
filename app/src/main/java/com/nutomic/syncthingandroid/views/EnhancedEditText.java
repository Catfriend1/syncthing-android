package com.nutomic.syncthingandroid.views;

import android.content.Context;
import androidx.appcompat.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import static android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION;

/**
 * Apparently EditText blocks touch event propagation to the parent even
 * when disabled/not clickable/not focusable. Therefore we have to manually
 * check whether we are enabled and either ignore the event or process it normally. <br/>
 * <br/>
 * This class also blocks the default EditText behaviour of textMultiLine flag enforcing replacement
 * of the IME action button with the new line character. This allows rendering soft wraps on single
 * line input.
 */
public class EnhancedEditText extends AppCompatEditText {

    public EnhancedEditText(Context context) {
        super(context);
    }

    public EnhancedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EnhancedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            super.performClick();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) {
            return false;
        }
        return super.performClick();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection conn = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~IME_FLAG_NO_ENTER_ACTION;
        return conn;
    }
}
