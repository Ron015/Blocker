package io.github.belafon.blocker;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.ContentInfo;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * An EditText that only accepts text typed on the keyboard: no copy, cut, paste, drag and drop,
 * autofill or text selection menu. (EmergencyUnlockActivity additionally rejects multi-character
 * inserts, which catches keyboards that paste from their own clipboard.)
 */
public class NoPasteEditText extends EditText {
    public NoPasteEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLongClickable(false);
        setTextIsSelectable(false);
        ActionMode.Callback noMenu = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        };
        setCustomSelectionActionModeCallback(noMenu);
        setCustomInsertionActionModeCallback(noMenu);
        setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+ routes paste, drag and drop and autofill through this listener.
            setOnReceiveContentListener(new String[] {"text/*", "image/*"}, (view, payload) -> {
                int source = payload.getSource();
                boolean fromClipboard = source == ContentInfo.SOURCE_CLIPBOARD
                        || source == ContentInfo.SOURCE_DRAG_AND_DROP
                        || source == ContentInfo.SOURCE_AUTOFILL
                        || source == ContentInfo.SOURCE_PROCESS_TEXT;
                return fromClipboard ? null : payload;
            });
        }
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return false; // refuse dropped text
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText
                || id == android.R.id.copy || id == android.R.id.cut
                || id == android.R.id.autofill) {
            return false;
        }
        return super.onTextContextMenuItem(id);
    }
}
