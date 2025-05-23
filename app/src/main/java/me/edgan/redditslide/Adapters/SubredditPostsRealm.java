package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;

import me.edgan.redditslide.Activities.NewsActivity;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostLoader;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.Synccit.MySynccitReadTask;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import me.edgan.redditslide.util.TimeUtils;

import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.DomainPaginator;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.SubredditPaginator;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * This class is reponsible for loading subreddit specific submissions {@link loadMore(Context,
 * SubmissionDisplay, boolean, String)} is implemented asynchronously.
 *
 * <p>Created by ccrama on 9/17/2015.
 */
public class SubredditPostsRealm implements PostLoader {
    public List<Submission> posts;
    public String subreddit;
    public String subredditRandom;
    public boolean nomore = false;
    public boolean offline;
    public boolean forced;
    public boolean loading;
    private Paginator paginator;
    public OfflineSubreddit cached;
    Context c;
    boolean force18;

    public SubredditPostsRealm(String subreddit, Context c) {
        posts = new ArrayList<>();
        this.subreddit = subreddit;
        this.c = c;
    }

    public SubredditPostsRealm(String subreddit, Context c, boolean force18) {
        posts = new ArrayList<>();
        this.subreddit = subreddit;
        this.c = c;
        this.force18 = force18;
    }

    @Override
    public void loadMore(
            final Context context, final SubmissionDisplay display, final boolean reset) {
        new LoadData(context, display, reset).execute(subreddit);
    }

    public void loadMore(
            Context context, SubmissionDisplay display, boolean reset, String subreddit) {
        this.subreddit = subreddit;
        loadMore(context, display, reset);
    }

    public ArrayList<String> all;

    @Override
    public List<Submission> getPosts() {
        return posts;
    }

    @Override
    public boolean hasMore() {
        return !nomore;
    }

    boolean authedOnce = false;
    boolean usedOffline;
    public long currentid;
    public SubmissionDisplay displayer;

    /** Asynchronous task for loading data */
    private class LoadData extends AsyncTask<String, Void, List<Submission>> {
        final boolean reset;
        Context context;

        public LoadData(Context context, SubmissionDisplay display, boolean reset) {
            this.context = context;
            displayer = display;
            this.reset = reset;
        }

        public int start;

        @Override
        public void onPostExecute(final List<Submission> submissions) {
            loading = false;
            if (error != null) {
                if (error instanceof NetworkException) {
                    NetworkException e = (NetworkException) error;
                    if (e.getResponse().getStatusCode() == 403 && !authedOnce) {
                        if (Reddit.authentication != null && Authentication.didOnline) {
                            Reddit.authentication.updateToken(context);
                        } else if (NetworkUtil.isConnected(context)
                                && Reddit.authentication == null) {
                            Reddit.authentication = new Authentication(context);
                        }
                        authedOnce = true;
                        loadMore(context, displayer, reset, subreddit);
                        return;
                    } else {
                        Toast.makeText(
                                        context,
                                        "A server error occurred, "
                                                + e.getResponse().getStatusCode()
                                                + (e.getResponse().getStatusMessage().isEmpty()
                                                        ? ""
                                                        : ": "
                                                                + e.getResponse()
                                                                        .getStatusMessage()),
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                if (error.getCause() instanceof UnknownHostException) {
                    Toast.makeText(
                                    context,
                                    "Loading failed, please check your internet connection",
                                    Toast.LENGTH_LONG)
                            .show();
                }
                displayer.updateError();
            } else if (submissions != null && !submissions.isEmpty()) {
                if (displayer instanceof SubmissionsView
                        && ((SubmissionsView) displayer).adapter.isError) {
                    ((SubmissionsView) displayer).adapter.undoSetError();
                }

                String[] ids = new String[submissions.size()];
                int i = 0;
                for (Submission s : submissions) {
                    ids[i] = s.getId();
                    i++;
                }

                // update online

                displayer.updateSuccess(posts, start);
                currentid = 0;
                OfflineSubreddit.currentid = currentid;

                if (subreddit.equals("random")
                        || subreddit.equals("myrandom")
                        || subreddit.equals("randnsfw")) {
                    subredditRandom = submissions.get(0).getSubredditName();
                }

                if (context instanceof SubredditView
                        && (subreddit.equals("random")
                                || subreddit.equals("myrandom")
                                || subreddit.equals("randnsfw"))) {
                    ((SubredditView) context).subreddit = subredditRandom;
                    ((SubredditView) context).executeAsyncSubreddit(subredditRandom);
                }
                if (!SettingValues.synccitName.isEmpty() && !offline) {
                    new MySynccitReadTask(displayer).execute(ids);
                }

            } else if (submissions != null) {
                // end of submissions
                nomore = true;
                displayer.updateSuccess(posts, posts.size() + 1);
            } else {
                if (!all.isEmpty() && !nomore && SettingValues.cache) {
                    if (context instanceof NewsActivity) {
                        doNewsActivityOffline(context, displayer);
                    }
                } else if (!nomore) {
                    // error
                    LogUtil.v("Setting error");
                    displayer.updateError();
                }
            }
        }

        @Override
        protected List<Submission> doInBackground(String... subredditPaginators) {
            if (BuildConfig.DEBUG) LogUtil.v("Loading data");
            if ((!NetworkUtil.isConnected(context) && !Authentication.didOnline)) {
                Log.v(LogUtil.getTag(), "Using offline data");
                offline = true;
                usedOffline = true;
                all = OfflineSubreddit.getAll(subreddit);
                return null;
            } else {
                offline = false;
                usedOffline = false;
            }

            if (reset || paginator == null) {
                offline = false;
                nomore = false;
                String sub = subredditPaginators[0].toLowerCase(Locale.ENGLISH);

                LogUtil.v(sub);

                if (sub.equals("frontpage")) {
                    paginator = new SubredditPaginator(Authentication.reddit);
                } else if (!sub.contains(".")) {
                    paginator = new SubredditPaginator(Authentication.reddit, sub);
                } else {
                    paginator = new DomainPaginator(Authentication.reddit, sub);
                }
                paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
                paginator.setLimit(Constants.PAGINATOR_POST_LIMIT);
            }

            List<Submission> filteredSubmissions = getNextFiltered();

            if (!(SettingValues.noImages
                    && ((!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile)
                            || SettingValues.lowResAlways))) {
                PhotoLoader.loadPhotos(c, filteredSubmissions);
            }
            if (SettingValues.storeHistory) {
                HasSeen.setHasSeenSubmission(filteredSubmissions);
                LastComments.setCommentsSince(filteredSubmissions);
            }
            SubmissionCache.cacheSubmissions(filteredSubmissions, context, subreddit);

            if (reset || offline || posts == null) {
                posts = new ArrayList<>(new LinkedHashSet<>(filteredSubmissions));
                start = -1;
            } else {
                posts.addAll(filteredSubmissions);
                posts = new ArrayList<>(new LinkedHashSet<>(posts));
                offline = false;
            }

            if (!usedOffline) {
                OfflineSubreddit.getSubNoLoad(subreddit.toLowerCase(Locale.ENGLISH))
                        .overwriteSubmissions(posts)
                        .writeToMemory(context);
            }
            start = 0;
            if (posts != null) {
                start = posts.size() + 1;
            }

            return filteredSubmissions;
        }

        public ArrayList<Submission> getNextFiltered() {
            ArrayList<Submission> filteredSubmissions = new ArrayList<>();
            ArrayList<Submission> adding = new ArrayList<>();

            try {
                if (paginator != null && paginator.hasNext()) {
                    if (force18 && paginator instanceof SubredditPaginator) {
                        ((SubredditPaginator) paginator).setObeyOver18(false);
                    }
                    adding.addAll(paginator.next());
                } else {
                    nomore = true;
                }

                for (Submission s : adding) {
                    if (!PostMatch.doesMatch(
                            s,
                            paginator instanceof SubredditPaginator
                                    ? ((SubredditPaginator) paginator).getSubreddit()
                                    : ((DomainPaginator) paginator).getDomain(),
                            force18)) {
                        filteredSubmissions.add(s);
                    }
                }
                if (paginator != null && paginator.hasNext() && filteredSubmissions.isEmpty()) {
                    filteredSubmissions.addAll(getNextFiltered());
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = e;
                if (e.getMessage() != null && e.getMessage().contains("Forbidden")) {
                    Reddit.authentication.updateToken(context);
                }
            }
            return filteredSubmissions;
        }

        Exception error;
    }

    public void doNewsActivityOffline(final Context c, final SubmissionDisplay displayer) {
        LogUtil.v(subreddit);
        if (all == null) {
            all = OfflineSubreddit.getAll(subreddit);
        }
        Collections.rotate(all, -1); // Move 0, or "submission only", to the end
        offline = true;

        final String[] titles = new String[all.size()];
        final String[] base = new String[all.size()];
        int i = 0;
        for (String s : all) {
            String[] split = s.split(",");
            titles[i] =
                    (Long.parseLong(split[1]) == 0
                            ? c.getString(R.string.settings_backup_submission_only)
                            : TimeUtils.getTimeAgo(Long.parseLong(split[1]), c)
                                    + c.getString(R.string.settings_backup_comments));
            base[i] = s;
            i++;
        }
        ((NewsActivity) c).getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        ((NewsActivity) c)
                .getSupportActionBar()
                .setListNavigationCallbacks(
                        new OfflineSubAdapter(c, android.R.layout.simple_list_item_1, titles),
                        new ActionBar.OnNavigationListener() {

                            @Override
                            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                                final String[] s2 = base[itemPosition].split(",");
                                OfflineSubreddit.currentid = Long.valueOf(s2[1]);
                                currentid = OfflineSubreddit.currentid;

                                new AsyncTask<Void, Void, Void>() {
                                    OfflineSubreddit cached;

                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        cached =
                                                OfflineSubreddit.getSubreddit(
                                                        subreddit, Long.valueOf(s2[1]), true, c);
                                        List<Submission> finalSubs = new ArrayList<>();
                                        for (Submission s : cached.submissions) {
                                            if (!PostMatch.doesMatch(s, subreddit, force18)) {
                                                finalSubs.add(s);
                                            }
                                        }

                                        posts = finalSubs;

                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(Void aVoid) {

                                        if (cached.submissions.isEmpty()) {
                                            displayer.updateOfflineError();
                                        }
                                        // update offline
                                        displayer.updateOffline(posts, Long.parseLong(s2[1]));
                                    }
                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                return true;
                            }
                        });
    }
}
