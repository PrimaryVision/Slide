package me.edgan.redditslide.Activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.SubredditSearchPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.SortingUtil;
import me.edgan.redditslide.util.TimeUtils;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.paginators.SubmissionSearchPaginator;
import net.dean.jraw.paginators.TimePeriod;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Locale;

public class Search extends BaseActivityAnim {

    public static final String EXTRA_TERM = "term";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_MULTIREDDIT = "multi";
    public static final String EXTRA_SITE = "site";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_SELF = "self";
    public static final String EXTRA_NSFW = "nsfw";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_TIME = "t";

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;

    private String where;
    private String subreddit;
    //    private String site;
    //    private String url;
    //    private boolean self;
    //    private boolean nsfw;
    //    private String author;

    private SubredditSearchPosts posts;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_search, menu);

        //   if (mShowInfoButton) menu.findItem(R.id.action_info).setVisible(true);
        //   else menu.findItem(R.id.action_info).setVisible(false);

        return true;
    }

    public void reloadSubs() {
        posts.refreshLayout.setRefreshing(true);
        posts.reset(time);
    }

    public void openTimeFramePopup() {
        final DialogInterface.OnClickListener l2 =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                time = TimePeriod.HOUR;
                                break;
                            case 1:
                                time = TimePeriod.DAY;
                                break;
                            case 2:
                                time = TimePeriod.WEEK;
                                break;
                            case 3:
                                time = TimePeriod.MONTH;
                                break;
                            case 4:
                                time = TimePeriod.YEAR;
                                break;
                            case 5:
                                time = TimePeriod.ALL;
                                break;
                        }
                        reloadSubs();

                        // When the .name() is returned for both of the ENUMs, it will be in all
                        // caps.
                        // So, make it lowercase, then capitalize the first letter of each.
                        getSupportActionBar()
                                .setSubtitle(
                                        StringUtils.capitalize(
                                                        SortingUtil.search
                                                                .name()
                                                                .toLowerCase(Locale.ENGLISH))
                                                + " › "
                                                + StringUtils.capitalize(
                                                        time.name().toLowerCase(Locale.ENGLISH)));
                    }
                };
        new AlertDialog.Builder(Search.this)
                .setTitle(R.string.sorting_time_choose)
                .setSingleChoiceItems(
                        SortingUtil.getSortingTimesStrings(),
                        SortingUtil.getSortingSearchId(this),
                        l2)
                .show();
    }

    public void openSearchTypePopup() {
        final DialogInterface.OnClickListener l2 =
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                SortingUtil.search = SubmissionSearchPaginator.SearchSort.RELEVANCE;
                                break;
                            case 1:
                                SortingUtil.search = SubmissionSearchPaginator.SearchSort.TOP;
                                break;
                            case 2:
                                SortingUtil.search = SubmissionSearchPaginator.SearchSort.NEW;
                                break;
                            case 3:
                                SortingUtil.search = SubmissionSearchPaginator.SearchSort.COMMENTS;
                                break;
                        }
                        reloadSubs();

                        // When the .name() is returned for both of the ENUMs, it will be in all
                        // caps.
                        // So, make it lowercase, then capitalize the first letter of each.
                        getSupportActionBar()
                                .setSubtitle(
                                        StringUtils.capitalize(
                                                        SortingUtil.search
                                                                .name()
                                                                .toLowerCase(Locale.ENGLISH))
                                                + " › "
                                                + StringUtils.capitalize(
                                                        time.name().toLowerCase(Locale.ENGLISH)));
                    }
                };
        new AlertDialog.Builder(Search.this)
                .setTitle(R.string.sorting_choose)
                .setSingleChoiceItems(SortingUtil.getSearch(), SortingUtil.getSearchType(), l2)
                .show();
    }

    public TimePeriod time;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.time:
                openTimeFramePopup();
                return true;
            case R.id.edit:
                MaterialDialog.Builder builder =
                        new MaterialDialog.Builder(this)
                                .title(R.string.search_title)
                                .alwaysCallInputCallback()
                                .input(
                                        getString(R.string.search_msg),
                                        where,
                                        new MaterialDialog.InputCallback() {
                                            @Override
                                            public void onInput(
                                                    MaterialDialog materialDialog,
                                                    CharSequence charSequence) {
                                                where = charSequence.toString();
                                            }
                                        });

                // Add "search current sub" if it is not frontpage/all/random
                builder.positiveText("Search")
                        .onPositive(
                                new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(
                                            @NonNull MaterialDialog materialDialog,
                                            @NonNull DialogAction dialogAction) {
                                        Intent i = new Intent(Search.this, Search.class);
                                        i.putExtra(Search.EXTRA_TERM, where);
                                        if (multireddit) {
                                            i.putExtra(Search.EXTRA_MULTIREDDIT, subreddit);
                                        } else {
                                            i.putExtra(Search.EXTRA_SUBREDDIT, subreddit);
                                        }
                                        startActivity(i);
                                        overridePendingTransition(0, 0);
                                        finish();
                                        overridePendingTransition(0, 0);
                                    }
                                });
                builder.show();
                return true;
            case R.id.sort:
                openSearchTypePopup();
                return true;
        }
        return false;
    }

    public boolean multireddit;
    RecyclerView rv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);

        applyColorTheme("");
        setContentView(R.layout.activity_search);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        where = getIntent().getExtras().getString(EXTRA_TERM, "");

        time = TimePeriod.ALL;

        if (getIntent().hasExtra(EXTRA_MULTIREDDIT)) {
            multireddit = true;
            subreddit = getIntent().getExtras().getString(EXTRA_MULTIREDDIT);
        } else {
            if (getIntent().hasExtra(EXTRA_AUTHOR)) {
                where = where + "&author=" + getIntent().getExtras().getString(EXTRA_AUTHOR);
            }
            if (getIntent().hasExtra(EXTRA_NSFW)) {
                where =
                        where
                                + "&nsfw="
                                + (getIntent().getExtras().getBoolean(EXTRA_NSFW) ? "yes" : "no");
            }
            if (getIntent().hasExtra(EXTRA_SELF)) {
                where =
                        where
                                + "&selftext="
                                + (getIntent().getExtras().getBoolean(EXTRA_SELF) ? "yes" : "no");
            }
            if (getIntent().hasExtra(EXTRA_SITE)) {
                where = where + "&site=" + getIntent().getExtras().getString(EXTRA_SITE);
            }
            if (getIntent().hasExtra(EXTRA_URL)) {
                where = where + "&url=" + getIntent().getExtras().getString(EXTRA_URL);
            }
            if (getIntent().hasExtra(EXTRA_TIME)) {
                TimePeriod timePeriod =
                        TimeUtils.stringToTimePeriod(getIntent().getExtras().getString(EXTRA_TIME));
                if (timePeriod != null) {
                    time = timePeriod;
                }
            }

            subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT, "");
        }

        where = StringEscapeUtils.unescapeHtml4(where);

        setupSubredditAppBar(R.id.toolbar, "Search", true, subreddit.toLowerCase(Locale.ENGLISH));

        getSupportActionBar().setTitle(CompatUtil.fromHtml(where));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        assert mToolbar != null; // it won't be, trust me
        mToolbar.setNavigationOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onBackPressed(); // Simulate a system's "Back" button functionality.
                    }
                });
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        // When the .name() is returned for both of the ENUMs, it will be in all caps.
        // So, make it lowercase, then capitalize the first letter of each.
        getSupportActionBar()
                .setSubtitle(
                        StringUtils.capitalize(
                                        SortingUtil.search.name().toLowerCase(Locale.ENGLISH))
                                + " › "
                                + StringUtils.capitalize(time.name().toLowerCase(Locale.ENGLISH)));

        rv = ((RecyclerView) findViewById(R.id.vertical_content));
        final RecyclerView.LayoutManager mLayoutManager =
                createLayoutManager(
                        LayoutUtils.getNumColumns(
                                getResources().getConfiguration().orientation, Search.this));
        rv.setLayoutManager(mLayoutManager);

        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(mToolbar, findViewById(R.id.header)) {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);

                        visibleItemCount = rv.getLayoutManager().getChildCount();
                        totalItemCount = rv.getLayoutManager().getItemCount();
                        if (rv.getLayoutManager() instanceof PreCachingLayoutManager) {
                            pastVisiblesItems =
                                    ((PreCachingLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPosition();
                        } else {
                            int[] firstVisibleItems = null;
                            firstVisibleItems =
                                    ((CatchStaggeredGridLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPositions(firstVisibleItems);
                            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                pastVisiblesItems = firstVisibleItems[0];
                            }
                        }

                        if (!posts.loading
                                && (visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount
                                && !posts.nomore) {
                            posts.loading = true;
                            posts.loadMore(adapter, subreddit, where, false, multireddit, time);
                        }
                    }
                });
        final SwipeRefreshLayout mSwipeRefreshLayout =
                (SwipeRefreshLayout) findViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(subreddit, this));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp.
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.SINGLE_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.SINGLE_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

        posts =
                new SubredditSearchPosts(
                        subreddit, where.toLowerCase(Locale.ENGLISH), this, multireddit);
        adapter = new ContributionAdapter(this, posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);
        // TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, subreddit, where, true, multireddit, time);
                        // TODO catch errors
                    }
                });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final int currentOrientation = newConfig.orientation;

        final CatchStaggeredGridLayoutManager mLayoutManager =
                (CatchStaggeredGridLayoutManager) rv.getLayoutManager();

        mLayoutManager.setSpanCount(LayoutUtils.getNumColumns(currentOrientation, Search.this));
    }

    @NonNull
    private RecyclerView.LayoutManager createLayoutManager(final int numColumns) {
        return new CatchStaggeredGridLayoutManager(
                numColumns, CatchStaggeredGridLayoutManager.VERTICAL);
    }
}
