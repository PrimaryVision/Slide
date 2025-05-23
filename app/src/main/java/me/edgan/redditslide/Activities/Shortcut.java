package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import me.edgan.redditslide.Adapters.SubChooseAdapter;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;

import java.util.ArrayList;

/** Created by ccrama on 10/2/2015. */
public class Shortcut extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getTheme().applyStyle(new FontPreferences(this).getCommentFontStyle().getResId(), true);
        getTheme().applyStyle(new FontPreferences(this).getPostFontStyle().getResId(), true);
        getTheme().applyStyle(new ColorPreferences(this).getFontStyle().getBaseId(), true);

        super.onCreate(savedInstanceState);
        doShortcut();
    }

    View header;

    public void doShortcut() {

        setContentView(R.layout.activity_setup_widget);

        if (SettingValues.oldSwipeMode) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.card_background, typedValue, true);
            getWindow().getDecorView().setBackgroundColor(typedValue.data);
        }

        setupAppBar(R.id.toolbar, R.string.shortcut_creation_title, true, true);
        header = getLayoutInflater().inflate(R.layout.shortcut_header, null);
        ListView list = (ListView) findViewById(R.id.subs);

        list.addHeaderView(header);

        final ArrayList<String> sorted =
                UserSubscriptions.getSubscriptionsForShortcut(Shortcut.this);
        final SubChooseAdapter adapter =
                new SubChooseAdapter(this, sorted, UserSubscriptions.getAllSubreddits(this));
        list.setAdapter(adapter);

        (header.findViewById(R.id.sort)).clearFocus();
        ((EditText) header.findViewById(R.id.sort))
                .addTextChangedListener(
                        new SimpleTextWatcher() {
                            @Override
                            public void afterTextChanged(Editable editable) {
                                final String result = editable.toString();
                                adapter.getFilter().filter(result);
                            }
                        });
    }
}
