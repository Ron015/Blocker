package io.github.belafon.blocker;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Shows the emergency code. It is drawn as an image, so there is no text that could be selected,
 * copied, read out by accessibility/assistant apps or picked up by "copy text" features.
 * FLAG_SECURE also blocks screenshots, screen recording and the recent apps preview.
 */
public class EmergencyCodeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_emergency_code);
        Screen.padForSystemBars(findViewById(R.id.root));
        ImageView code = findViewById(R.id.code);
        code.setImageBitmap(renderCode());
    }

    private Bitmap renderCode() {
        TypedValue color = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(getColorStateList(color.resourceId).getDefaultColor());
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics()));

        String[] lines = EmergencyCode.formatted().split("\n");
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) * 1.6f;
        float width = 0;
        for (String line : lines) width = Math.max(width, paint.measureText(line));

        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(width) + 2,
                (int) Math.ceil(lineHeight * lines.length), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], 0, i * lineHeight - fm.ascent, paint);
        }
        return bitmap;
    }
}
