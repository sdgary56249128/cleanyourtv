package com.gathin.tvhome;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

/** Apply the selected font when Home and its app tiles are created. */
public final class FontHomeActivity extends MainActivity {
    private RecyclerView grid;
    private Typeface font;
    private final RecyclerView.OnChildAttachStateChangeListener fontListener =
            new RecyclerView.OnChildAttachStateChangeListener() {
                @Override public void onChildViewAttachedToWindow(View view) { FontChoice.apply(view, font); }
                @Override public void onChildViewDetachedFromWindow(View view) {}
            };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = FontChoice.face(this, FontChoice.selected(this));
        FontChoice.apply(getWindow().getDecorView(), font);
        grid = findViewById(R.id.grid);
        if (font != null) grid.addOnChildAttachStateChangeListener(fontListener);
    }

    @Override protected void onDestroy() {
        if (grid != null) grid.removeOnChildAttachStateChangeListener(fontListener);
        super.onDestroy();
    }
}
