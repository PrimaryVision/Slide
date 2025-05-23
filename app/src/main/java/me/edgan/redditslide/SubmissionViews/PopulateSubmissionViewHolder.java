package me.edgan.redditslide.SubmissionViews;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;
import static me.edgan.redditslide.util.SubmissionBottomSheetActions.hideSubmission;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.cocosw.bottomsheet.BottomSheet;
import com.devspark.robototextview.RobotoTypefaces;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.snackbar.Snackbar;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.AlbumPager;
import me.edgan.redditslide.Activities.FullscreenVideo;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.MultiredditOverview;
import me.edgan.redditslide.Activities.PostReadLater;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Activities.RedditGallery;
import me.edgan.redditslide.Activities.RedditGalleryPager;
import me.edgan.redditslide.Activities.Search;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Activities.Tumblr;
import me.edgan.redditslide.Activities.TumblrPager;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CommentCacheAsync;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.DataShare;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.Vote;
import me.edgan.redditslide.util.AnimatorUtil;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.ClipboardUtil;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.DisplayUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.JsonUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SubmissionBottomSheetActions;
import me.edgan.redditslide.util.SubmissionModActions;
import me.edgan.redditslide.util.SubmissionParser;

import net.dean.jraw.ApiException;
import net.dean.jraw.fluent.FlairReference;
import net.dean.jraw.fluent.FluentRedditClient;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.ModerationManager;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.FlairTemplate;
import net.dean.jraw.models.Ruleset;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditRule;
import net.dean.jraw.models.VoteDirection;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Created by ccrama on 9/19/2015. */
public class PopulateSubmissionViewHolder {

    public PopulateSubmissionViewHolder() {}

    private static void addClickFunctions(
            final View base,
            final ContentType.Type type,
            final Activity contextActivity,
            final Submission submission,
            final SubmissionViewHolder holder,
            final boolean full) {
        base.setOnClickListener(
                new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View v) {
                        if (NetworkUtil.isConnected(contextActivity)
                                || (!NetworkUtil.isConnected(contextActivity)
                                        && ContentType.fullImage(type))) {
                            if (SettingValues.storeHistory && !full) {
                                if (!submission.isNsfw() || SettingValues.storeNSFWHistory) {
                                    HasSeen.addSeen(submission.getFullName());
                                    if (contextActivity instanceof MainActivity
                                            || contextActivity instanceof MultiredditOverview
                                            || contextActivity instanceof SubredditView
                                            || contextActivity instanceof Search
                                            || contextActivity instanceof Profile) {
                                        holder.title.setAlpha(0.54f);
                                        holder.body.setAlpha(0.54f);
                                    }
                                }
                            }
                            if (!(contextActivity instanceof PeekViewActivity)
                                    || !((PeekViewActivity) contextActivity).isPeeking()
                                    || (base instanceof HeaderImageLinkView
                                            && ((HeaderImageLinkView) base).popped)) {
                                if (!PostMatch.openExternal(submission.getUrl())
                                        || type == ContentType.Type.VIDEO) {
                                    switch (type) {
                                        case STREAMABLE:
                                            if (SettingValues.video) {
                                                Intent myIntent =
                                                        new Intent(
                                                                contextActivity, MediaView.class);
                                                myIntent.putExtra(
                                                        MediaView.SUBREDDIT,
                                                        submission.getSubredditName());
                                                myIntent.putExtra(
                                                        MediaView.EXTRA_URL, submission.getUrl());
                                                myIntent.putExtra(
                                                        EXTRA_SUBMISSION_TITLE,
                                                        submission.getTitle());
                                                PopulateBase.addAdaptorPosition(
                                                        myIntent,
                                                        submission,
                                                        holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(myIntent);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case IMGUR:
                                        case DEVIANTART:
                                        case XKCD:
                                        case IMAGE:
                                            openImage(
                                                    type,
                                                    contextActivity,
                                                    submission,
                                                    holder.leadImage,
                                                    holder.getBindingAdapterPosition());
                                            break;
                                        case EMBEDDED:
                                            if (SettingValues.video) {
                                                String data =
                                                        CompatUtil.fromHtml(
                                                                        submission
                                                                                .getDataNode()
                                                                                .get("media_embed")
                                                                                .get("content")
                                                                                .asText())
                                                                .toString();
                                                {
                                                    Intent i =
                                                            new Intent(
                                                                    contextActivity,
                                                                    FullscreenVideo.class);
                                                    i.putExtra(FullscreenVideo.EXTRA_HTML, data);
                                                    contextActivity.startActivity(i);
                                                }
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case REDDIT:
                                            openRedditContent(submission.getUrl(), contextActivity);
                                            break;
                                        case REDDIT_GALLERY:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i =
                                                            new Intent(
                                                                    contextActivity,
                                                                    RedditGalleryPager.class);
                                                    i.putExtra(
                                                            AlbumPager.SUBREDDIT,
                                                            submission.getSubredditName());
                                                } else {
                                                    i =
                                                            new Intent(
                                                                    contextActivity,
                                                                    RedditGallery.class);
                                                    i.putExtra(
                                                            Album.SUBREDDIT,
                                                            submission.getSubredditName());
                                                }
                                                i.putExtra(
                                                        EXTRA_SUBMISSION_TITLE,
                                                        submission.getTitle());

                                                i.putExtra(
                                                        RedditGallery.SUBREDDIT,
                                                        submission.getSubredditName());

                                                ArrayList<GalleryImage> urls = new ArrayList<>();

                                                JsonNode dataNode = submission.getDataNode();
                                                if (dataNode.has("gallery_data")) {
                                                    JsonUtil.getGalleryData(dataNode, urls);
                                                } else if (dataNode.has(
                                                        "crosspost_parent_list")) { // Else, try
                                                    // getting
                                                    // crosspost
                                                    // gallery data
                                                    JsonNode crosspost_parent =
                                                            dataNode.get("crosspost_parent_list")
                                                                    .get(0);
                                                    if (crosspost_parent.has("gallery_data")) {
                                                        JsonUtil.getGalleryData(
                                                                crosspost_parent, urls);
                                                    }
                                                }

                                                Bundle urlsBundle = new Bundle();
                                                urlsBundle.putSerializable(
                                                        RedditGallery.GALLERY_URLS, urls);
                                                i.putExtras(urlsBundle);

                                                PopulateBase.addAdaptorPosition(
                                                        i,
                                                        submission,
                                                        holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(
                                                        R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case LINK:
                                            LinkUtil.openUrl(
                                                    submission.getUrl(),
                                                    Palette.getColor(submission.getSubredditName()),
                                                    contextActivity,
                                                    holder.getBindingAdapterPosition(),
                                                    submission);
                                            break;
                                        case SELF:
                                            if (holder != null) {
                                                OnSingleClickListener.override = true;
                                                holder.itemView.performClick();
                                            }
                                            break;
                                        case ALBUM:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i =
                                                            new Intent(
                                                                    contextActivity,
                                                                    AlbumPager.class);
                                                    i.putExtra(
                                                            AlbumPager.SUBREDDIT,
                                                            submission.getSubredditName());
                                                } else {
                                                    i = new Intent(contextActivity, Album.class);
                                                    i.putExtra(
                                                            Album.SUBREDDIT,
                                                            submission.getSubredditName());
                                                }
                                                i.putExtra(
                                                        EXTRA_SUBMISSION_TITLE,
                                                        submission.getTitle());
                                                i.putExtra(Album.EXTRA_URL, submission.getUrl());

                                                PopulateBase.addAdaptorPosition(
                                                        i,
                                                        submission,
                                                        holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(
                                                        R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case TUMBLR:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i =
                                                            new Intent(
                                                                    contextActivity,
                                                                    TumblrPager.class);
                                                    i.putExtra(
                                                            TumblrPager.SUBREDDIT,
                                                            submission.getSubredditName());
                                                } else {
                                                    i = new Intent(contextActivity, Tumblr.class);
                                                    i.putExtra(
                                                            Tumblr.SUBREDDIT,
                                                            submission.getSubredditName());
                                                }
                                                i.putExtra(Album.EXTRA_URL, submission.getUrl());

                                                PopulateBase.addAdaptorPosition(
                                                        i,
                                                        submission,
                                                        holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(
                                                        R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case VREDDIT_REDIRECT:
                                        case GIF:
                                        case VREDDIT_DIRECT:
                                            openGif(
                                                    contextActivity,
                                                    submission,
                                                    holder.getBindingAdapterPosition());
                                            break;
                                        case NONE:
                                            if (holder != null) {
                                                holder.itemView.performClick();
                                            }
                                            break;
                                        case VIDEO:
                                            if (!LinkUtil.tryOpenWithVideoPlugin(
                                                    submission.getUrl())) {
                                                LinkUtil.openUrl(
                                                        submission.getUrl(),
                                                        Palette.getStatusBarColor(),
                                                        contextActivity);
                                            }
                                            break;
                                    }
                                } else {
                                    LinkUtil.openExternally(submission.getUrl());
                                }
                            }
                        } else {
                            if (!(contextActivity instanceof PeekViewActivity)
                                    || !((PeekViewActivity) contextActivity).isPeeking()) {

                                Snackbar s =
                                        Snackbar.make(
                                                holder.itemView,
                                                R.string.go_online_view_content,
                                                Snackbar.LENGTH_SHORT);
                                LayoutUtils.showSnackbar(s);
                            }
                        }
                    }
                });
    }

    public static void openRedditContent(String url, Context c) {
        OpenRedditLink.openUrl(c, url, true);
    }

    public static void openImage(
            ContentType.Type type,
            Activity contextActivity,
            Submission submission,
            HeaderImageLinkView baseView,
            int adapterPosition) {
        if (SettingValues.image) {
            Intent myIntent = new Intent(contextActivity, MediaView.class);
            myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
            myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());
            String previewUrl;
            String url = submission.getUrl();

            if (baseView != null
                    && baseView.lq
                    && SettingValues.loadImageLq
                    && type != ContentType.Type.XKCD) {
                myIntent.putExtra(MediaView.EXTRA_LQ, true);
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, baseView.loadedUrl);
            } else if (submission.getDataNode().has("preview")
                    && submission
                            .getDataNode()
                            .get("preview")
                            .get("images")
                            .get(0)
                            .get("source")
                            .has("height")
                    && type
                            != ContentType.Type
                                    .XKCD) { // Load the preview image which has probably already
                // been cached in memory instead of the direct link
                previewUrl =
                        submission
                                .getDataNode()
                                .get("preview")
                                .get("images")
                                .get(0)
                                .get("source")
                                .get("url")
                                .asText();
                if (baseView == null || (!SettingValues.loadImageLq && baseView.lq)) {
                    myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, previewUrl);
                } else {
                    myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, baseView.loadedUrl);
                }
            }
            myIntent.putExtra(MediaView.EXTRA_URL, url);
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition);
            myIntent.putExtra(MediaView.EXTRA_SHARE_URL, submission.getUrl());

            contextActivity.startActivity(myIntent);

        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }

    public static void openGif(
            Activity contextActivity, Submission submission, int adapterPosition) {
        if (SettingValues.gif) {
            DataShare.sharedSubmission = submission;

            Intent myIntent = new Intent(contextActivity, MediaView.class);
            myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
            myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());

            GifUtils.AsyncLoadGif.VideoType t =
                    GifUtils.AsyncLoadGif.getVideoType(submission.getUrl());

            if (t == GifUtils.AsyncLoadGif.VideoType.VREDDIT) {
                if (submission.getDataNode().has("media")
                        && submission.getDataNode().get("media").has("reddit_video")
                        && submission
                                .getDataNode()
                                .get("media")
                                .get("reddit_video")
                                .has("hls_url")) {
                    myIntent.putExtra(
                            MediaView.EXTRA_URL,
                            StringEscapeUtils.unescapeJson(
                                            submission
                                                    .getDataNode()
                                                    .get("media")
                                                    .get("reddit_video")
                                                    .get("dash_url") // In the future, we could
                                                    // load the HLS url as well
                                                    .asText())
                                    .replace("&amp;", "&"));
                } else if (submission.getDataNode().has("media")
                        && submission.getDataNode().get("media").has("reddit_video")) {
                    myIntent.putExtra(
                            MediaView.EXTRA_URL,
                            StringEscapeUtils.unescapeJson(
                                            submission
                                                    .getDataNode()
                                                    .get("media")
                                                    .get("reddit_video")
                                                    .get("fallback_url")
                                                    .asText())
                                    .replace("&amp;", "&"));
                } else if (submission.getDataNode().has("crosspost_parent_list")) {
                    myIntent.putExtra(
                            MediaView.EXTRA_URL,
                            StringEscapeUtils.unescapeJson(
                                            submission
                                                    .getDataNode()
                                                    .get("crosspost_parent_list")
                                                    .get(0)
                                                    .get("media")
                                                    .get("reddit_video")
                                                    .get("dash_url")
                                                    .asText())
                                    .replace("&amp;", "&"));
                } else {
                    new OpenVRedditTask(contextActivity, submission.getSubredditName())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, submission.getUrl());
                    return;
                }

            } else if (t.shouldLoadPreview()
                    && submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").get("images").get(0).has("variants")
                    && submission
                            .getDataNode()
                            .get("preview")
                            .get("images")
                            .get(0)
                            .get("variants")
                            .has("mp4")) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                                        submission
                                                .getDataNode()
                                                .get("preview")
                                                .get("images")
                                                .get(0)
                                                .get("variants")
                                                .get("mp4")
                                                .get("source")
                                                .get("url")
                                                .asText())
                                .replace("&amp;", "&"));
            } else if (t.shouldLoadPreview()
                    && submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").has("reddit_video_preview")
                    && submission.getDataNode().get("preview").get("reddit_video_preview").has("fallback_url")
                    && submission
                            .getDataNode()
                            .get("preview")
                            .get("reddit_video_preview")
                            .has("fallback_url")) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                                        submission
                                                .getDataNode()
                                                .get("preview")
                                                .get("reddit_video_preview")
                                                .get("fallback_url")
                                                .asText())
                                .replace("&amp;", "&"));
            } else if (t == GifUtils.AsyncLoadGif.VideoType.DIRECT
                    && submission.getDataNode().has("media")
                    && submission.getDataNode().get("media").has("reddit_video")
                    && submission
                            .getDataNode()
                            .get("media")
                            .get("reddit_video")
                            .has("fallback_url")) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                                        submission
                                                .getDataNode()
                                                .get("media")
                                                .get("reddit_video")
                                                .get("fallback_url")
                                                .asText())
                                .replace("&amp;", "&"));

            } else if (t != GifUtils.AsyncLoadGif.VideoType.OTHER) {
                myIntent.putExtra(MediaView.EXTRA_URL, submission.getUrl());
            } else {
                LinkUtil.openUrl(
                        submission.getUrl(),
                        Palette.getColor(submission.getSubredditName()),
                        contextActivity,
                        adapterPosition,
                        submission);
                return;
            }
            if (submission.getDataNode().has("preview")
                    && submission
                            .getDataNode()
                            .get("preview")
                            .get("images")
                            .get(0)
                            .get("source")
                            .has("height")) { // Load the preview image which has probably
                // already been cached in memory instead of the
                // direct link
                String previewUrl =
                        submission
                                .getDataNode()
                                .get("preview")
                                .get("images")
                                .get(0)
                                .get("source")
                                .get("url")
                                .asText();
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, previewUrl);
            }
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition);
            contextActivity.startActivity(myIntent);
        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }

    public String reason;

    boolean[] chosen = new boolean[] {false, false, false};
    boolean[] oldChosen = new boolean[] {false, false, false};

    public <T extends Contribution> void showBottomSheet(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder,
            final List<T> posts,
            final String baseSub,
            final RecyclerView recyclerview,
            final boolean full) {

        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = mContext.obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable profile =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_account_circle, null);
        final Drawable sub =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_bookmark_border, null);
        Drawable saved =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_star, null);
        Drawable hide =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_visibility_off, null);
        final Drawable report =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_report, null);
        Drawable copy =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_content_copy, null);
        final Drawable readLater =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_download, null);
        Drawable open =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_open_in_browser, null);
        Drawable link =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_link, null);
        Drawable reddit =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_forum, null);
        Drawable filter =
                ResourcesCompat.getDrawable(
                        mContext.getResources(), R.drawable.ic_filter_list, null);
        Drawable crosspost =
                ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_forward, null);

        final List<Drawable> drawableSet =
                Arrays.asList(
                        profile, sub, saved, hide, report, copy, open, link, reddit, readLater,
                        filter, crosspost);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        ta.recycle();

        final BottomSheet.Builder b =
                new BottomSheet.Builder(mContext).title(CompatUtil.fromHtml(submission.getTitle()));

        final boolean isReadLater = mContext instanceof PostReadLater;
        final boolean isAddedToReadLaterList = ReadLater.isToBeReadLater(submission);
        if (Authentication.didOnline) {
            b.sheet(1, profile, "/u/" + submission.getAuthor())
                    .sheet(2, sub, "/r/" + submission.getSubredditName());
            String save = mContext.getString(R.string.btn_save);
            if (ActionStates.isSaved(submission)) {
                save = mContext.getString(R.string.comment_unsave);
            }
            if (Authentication.isLoggedIn) {
                b.sheet(3, saved, save);
            }
        }

        if (isAddedToReadLaterList) {
            CharSequence markAsReadCs = mContext.getString(R.string.mark_as_read);
            b.sheet(28, readLater, markAsReadCs);
        } else {
            CharSequence readLaterCs = mContext.getString(R.string.read_later);
            b.sheet(28, readLater, readLaterCs);
        }

        if (Authentication.didOnline) {
            if (Authentication.isLoggedIn) {
                b.sheet(12, report, mContext.getString(R.string.btn_report));
                b.sheet(13, crosspost, mContext.getString(R.string.btn_crosspost));
            }
        }

        if (submission.getSelftext() != null && !submission.getSelftext().isEmpty() && full) {
            b.sheet(25, copy, mContext.getString(R.string.submission_copy_text));
        }

        boolean hidden = submission.isHidden();
        if (!full && Authentication.didOnline) {
            if (!hidden) {
                b.sheet(5, hide, mContext.getString(R.string.submission_hide));
            } else {
                b.sheet(5, hide, mContext.getString(R.string.submission_unhide));
            }
        }
        b.sheet(7, open, mContext.getString(R.string.open_externally));

        b.sheet(4, link, mContext.getString(R.string.submission_share_permalink))
                .sheet(8, reddit, mContext.getString(R.string.submission_share_reddit_url));
        if ((mContext instanceof MainActivity) || (mContext instanceof SubredditView)) {
            b.sheet(10, filter, mContext.getString(R.string.filter_content));
        }

        b.listener(
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 1:
                                {
                                    Intent i = new Intent(mContext, Profile.class);
                                    i.putExtra(Profile.EXTRA_PROFILE, submission.getAuthor());
                                    mContext.startActivity(i);
                                }
                                break;
                            case 2:
                                {
                                    Intent i = new Intent(mContext, SubredditView.class);
                                    i.putExtra(
                                            SubredditView.EXTRA_SUBREDDIT,
                                            submission.getSubredditName());
                                    mContext.startActivityForResult(i, 14);
                                }
                                break;
                            case 10:
                                String[] choices;
                                final String flair =
                                        submission.getSubmissionFlair().getText() != null
                                                ? submission.getSubmissionFlair().getText()
                                                : "";
                                if (flair.isEmpty()) {
                                    choices =
                                            new String[] {
                                                mContext.getString(
                                                        R.string.filter_posts_sub,
                                                        submission.getSubredditName()),
                                                mContext.getString(
                                                        R.string.filter_posts_user,
                                                        submission.getAuthor()),
                                                mContext.getString(
                                                        R.string.filter_posts_urls,
                                                        submission.getDomain()),
                                                mContext.getString(
                                                        R.string.filter_open_externally,
                                                        submission.getDomain())
                                            };

                                    chosen =
                                            new boolean[] {
                                                SettingValues.subredditFilters.contains(
                                                        submission
                                                                .getSubredditName()
                                                                .toLowerCase(Locale.ENGLISH)),
                                                SettingValues.userFilters.contains(
                                                        submission
                                                                .getAuthor()
                                                                .toLowerCase(Locale.ENGLISH)),
                                                SettingValues.domainFilters.contains(
                                                        submission
                                                                .getDomain()
                                                                .toLowerCase(Locale.ENGLISH)),
                                                SettingValues.alwaysExternal.contains(
                                                        submission
                                                                .getDomain()
                                                                .toLowerCase(Locale.ENGLISH))
                                            };
                                    oldChosen = chosen.clone();
                                } else {
                                    choices =
                                            new String[] {
                                                mContext.getString(
                                                        R.string.filter_posts_sub,
                                                        submission.getSubredditName()),
                                                mContext.getString(
                                                        R.string.filter_posts_user,
                                                        submission.getAuthor()),
                                                mContext.getString(
                                                        R.string.filter_posts_urls,
                                                        submission.getDomain()),
                                                mContext.getString(
                                                        R.string.filter_open_externally,
                                                        submission.getDomain()),
                                                mContext.getString(
                                                        R.string.filter_posts_flair, flair, baseSub)
                                            };
                                }
                                chosen =
                                        new boolean[] {
                                            SettingValues.subredditFilters.contains(
                                                    submission
                                                            .getSubredditName()
                                                            .toLowerCase(Locale.ENGLISH)),
                                            SettingValues.userFilters.contains(
                                                    submission
                                                            .getAuthor()
                                                            .toLowerCase(Locale.ENGLISH)),
                                            SettingValues.domainFilters.contains(
                                                    submission
                                                            .getDomain()
                                                            .toLowerCase(Locale.ENGLISH)),
                                            SettingValues.alwaysExternal.contains(
                                                    submission
                                                            .getDomain()
                                                            .toLowerCase(Locale.ENGLISH)),
                                            SettingValues.flairFilters.contains(
                                                    baseSub
                                                            + ":"
                                                            + flair.toLowerCase(Locale.ENGLISH)
                                                                    .trim())
                                        };
                                oldChosen = chosen.clone();

                                new AlertDialog.Builder(mContext)
                                        .setTitle(R.string.filter_title)
                                        .setMultiChoiceItems(
                                                choices,
                                                chosen,
                                                (dialog1, which1, isChecked) ->
                                                        chosen[which1] = isChecked)
                                        .setPositiveButton(
                                                R.string.filter_btn,
                                                (dialog12, which12) -> {
                                                    boolean filtered = false;
                                                    SharedPreferences.Editor e =
                                                            SettingValues.prefs.edit();
                                                    if (chosen[0] && chosen[0] != oldChosen[0]) {
                                                        SettingValues.subredditFilters.add(
                                                                submission
                                                                        .getSubredditName()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = true;
                                                        e.putStringSet(
                                                                SettingValues
                                                                        .PREF_SUBREDDIT_FILTERS,
                                                                SettingValues.subredditFilters);
                                                    } else if (!chosen[0]
                                                            && chosen[0] != oldChosen[0]) {
                                                        SettingValues.subredditFilters.remove(
                                                                submission
                                                                        .getSubredditName()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = false;
                                                        e.putStringSet(
                                                                SettingValues
                                                                        .PREF_SUBREDDIT_FILTERS,
                                                                SettingValues.subredditFilters);
                                                        e.apply();
                                                    }
                                                    if (chosen[1] && chosen[1] != oldChosen[1]) {
                                                        SettingValues.userFilters.add(
                                                                submission
                                                                        .getAuthor()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = true;
                                                        e.putStringSet(
                                                                SettingValues.PREF_USER_FILTERS,
                                                                SettingValues.userFilters);
                                                    } else if (!chosen[1]
                                                            && chosen[1] != oldChosen[1]) {
                                                        SettingValues.userFilters.remove(
                                                                submission
                                                                        .getAuthor()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = false;
                                                        e.putStringSet(
                                                                SettingValues.PREF_USER_FILTERS,
                                                                SettingValues.userFilters);
                                                        e.apply();
                                                    }
                                                    if (chosen[2] && chosen[2] != oldChosen[2]) {
                                                        SettingValues.domainFilters.add(
                                                                submission
                                                                        .getDomain()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = true;
                                                        e.putStringSet(
                                                                SettingValues.PREF_DOMAIN_FILTERS,
                                                                SettingValues.domainFilters);
                                                    } else if (!chosen[2]
                                                            && chosen[2] != oldChosen[2]) {
                                                        SettingValues.domainFilters.remove(
                                                                submission
                                                                        .getDomain()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        filtered = false;
                                                        e.putStringSet(
                                                                SettingValues.PREF_DOMAIN_FILTERS,
                                                                SettingValues.domainFilters);
                                                        e.apply();
                                                    }
                                                    if (chosen[3] && chosen[3] != oldChosen[3]) {
                                                        SettingValues.alwaysExternal.add(
                                                                submission
                                                                        .getDomain()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        e.putStringSet(
                                                                SettingValues.PREF_ALWAYS_EXTERNAL,
                                                                SettingValues.alwaysExternal);
                                                        e.apply();
                                                    } else if (!chosen[3]
                                                            && chosen[3] != oldChosen[3]) {
                                                        SettingValues.alwaysExternal.remove(
                                                                submission
                                                                        .getDomain()
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim());
                                                        e.putStringSet(
                                                                SettingValues.PREF_ALWAYS_EXTERNAL,
                                                                SettingValues.alwaysExternal);
                                                        e.apply();
                                                    }
                                                    if (chosen.length > 4) {
                                                        String s =
                                                                (baseSub + ":" + flair)
                                                                        .toLowerCase(Locale.ENGLISH)
                                                                        .trim();
                                                        if (chosen[4]
                                                                && chosen[4] != oldChosen[4]) {
                                                            SettingValues.flairFilters.add(s);
                                                            e.putStringSet(
                                                                    SettingValues
                                                                            .PREF_FLAIR_FILTERS,
                                                                    SettingValues.flairFilters);
                                                            e.apply();
                                                            filtered = true;
                                                        } else if (!chosen[4]
                                                                && chosen[4] != oldChosen[4]) {
                                                            SettingValues.flairFilters.remove(s);
                                                            e.putStringSet(
                                                                    SettingValues
                                                                            .PREF_FLAIR_FILTERS,
                                                                    SettingValues.flairFilters);
                                                            e.apply();
                                                        }
                                                    }
                                                    if (filtered) {
                                                        e.apply();
                                                        ArrayList<Contribution> toRemove =
                                                                new ArrayList<>();
                                                        for (Contribution s : posts) {
                                                            if (s instanceof Submission
                                                                    && PostMatch.doesMatch(
                                                                            (Submission) s)) {
                                                                toRemove.add(s);
                                                            }
                                                        }

                                                        OfflineSubreddit s =
                                                                OfflineSubreddit.getSubreddit(
                                                                        baseSub, false, mContext);

                                                        for (Contribution remove : toRemove) {
                                                            final int pos = posts.indexOf(remove);
                                                            posts.remove(pos);
                                                            if (baseSub != null) {
                                                                s.hideMulti(pos);
                                                            }
                                                        }
                                                        s.writeToMemoryNoStorage();
                                                        recyclerview
                                                                .getAdapter()
                                                                .notifyDataSetChanged();
                                                    }
                                                })
                                        .setNegativeButton(R.string.btn_cancel, null)
                                        .show();
                                break;

                            case 3:
                                saveSubmission(submission, mContext, holder, full);
                                break;
                            case 5:
                                {
                                    hideSubmission(
                                            submission, posts, baseSub, recyclerview, mContext);
                                }
                                break;
                            case 7:
                                LinkUtil.openExternally(submission.getUrl());
                                if (submission.isNsfw() && !SettingValues.storeNSFWHistory) {
                                    // Do nothing if the post is NSFW and storeNSFWHistory is not
                                    // enabled
                                } else if (SettingValues.storeHistory) {
                                    HasSeen.addSeen(submission.getFullName());
                                }
                                break;
                            case 13:
                                LinkUtil.crosspost(submission, mContext);
                                break;
                            case 28:
                                if (!isAddedToReadLaterList) {
                                    ReadLater.setReadLater(submission, true);
                                    Snackbar s =
                                            Snackbar.make(
                                                    holder.itemView,
                                                    "Added to read later!",
                                                    Snackbar.LENGTH_SHORT);
                                    View view = s.getView();
                                    TextView tv =
                                            view.findViewById(
                                                    com.google.android.material.R.id.snackbar_text);
                                    tv.setTextColor(Color.WHITE);
                                    s.setAction(
                                            R.string.btn_undo,
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    ReadLater.setReadLater(submission, false);
                                                    Snackbar s2 =
                                                            Snackbar.make(
                                                                    holder.itemView,
                                                                    "Removed from read later",
                                                                    Snackbar.LENGTH_SHORT);
                                                    LayoutUtils.showSnackbar(s2);
                                                }
                                            });
                                    if (NetworkUtil.isConnected(mContext)) {
                                        new CommentCacheAsync(
                                                        Collections.singletonList(submission),
                                                        mContext,
                                                        CommentCacheAsync.SAVED_SUBMISSIONS,
                                                        new boolean[] {true, true})
                                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    }
                                    s.show();
                                } else {
                                    ReadLater.setReadLater(submission, false);
                                    if (isReadLater || !Authentication.didOnline) {
                                        final int pos = posts.indexOf(submission);
                                        posts.remove(submission);

                                        recyclerview
                                                .getAdapter()
                                                .notifyItemRemoved(
                                                        holder.getBindingAdapterPosition());

                                        Snackbar s2 =
                                                Snackbar.make(
                                                        holder.itemView,
                                                        "Removed from read later",
                                                        Snackbar.LENGTH_SHORT);
                                        View view2 = s2.getView();
                                        TextView tv2 =
                                                view2.findViewById(
                                                        com.google.android.material.R.id
                                                                .snackbar_text);
                                        tv2.setTextColor(Color.WHITE);
                                        s2.setAction(
                                                R.string.btn_undo,
                                                new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        posts.add(pos, (T) submission);
                                                        recyclerview
                                                                .getAdapter()
                                                                .notifyDataSetChanged();
                                                    }
                                                });
                                    } else {
                                        Snackbar s2 =
                                                Snackbar.make(
                                                        holder.itemView,
                                                        "Removed from read later",
                                                        Snackbar.LENGTH_SHORT);
                                        View view2 = s2.getView();
                                        TextView tv2 =
                                                view2.findViewById(
                                                        com.google.android.material.R.id
                                                                .snackbar_text);
                                        s2.show();
                                    }
                                    OfflineSubreddit.newSubreddit(
                                                    CommentCacheAsync.SAVED_SUBMISSIONS)
                                            .deleteFromMemory(submission.getFullName());
                                }
                                break;
                            case 4:
                                Reddit.defaultShareText(
                                        CompatUtil.fromHtml(submission.getTitle()).toString(),
                                        StringEscapeUtils.escapeHtml4(submission.getUrl()),
                                        mContext);
                                break;
                            case 12:
                                final MaterialDialog reportDialog =
                                        new MaterialDialog.Builder(mContext)
                                                .customView(R.layout.report_dialog, true)
                                                .title(R.string.report_post)
                                                .positiveText(R.string.btn_report)
                                                .negativeText(R.string.btn_cancel)
                                                .onPositive(
                                                        new MaterialDialog.SingleButtonCallback() {
                                                            @Override
                                                            public void onClick(
                                                                    MaterialDialog dialog,
                                                                    DialogAction which) {
                                                                RadioGroup reasonGroup =
                                                                        dialog.getCustomView()
                                                                                .findViewById(
                                                                                        R.id
                                                                                                .report_reasons);
                                                                String reportReason;
                                                                if (reasonGroup
                                                                                .getCheckedRadioButtonId()
                                                                        == R.id.report_other) {
                                                                    reportReason =
                                                                            ((EditText)
                                                                                            dialog.getCustomView()
                                                                                                    .findViewById(
                                                                                                            R
                                                                                                                    .id
                                                                                                                    .input_report_reason))
                                                                                    .getText()
                                                                                    .toString();
                                                                } else {
                                                                    reportReason =
                                                                            ((RadioButton)
                                                                                            reasonGroup
                                                                                                    .findViewById(
                                                                                                            reasonGroup
                                                                                                                    .getCheckedRadioButtonId()))
                                                                                    .getText()
                                                                                    .toString();
                                                                }
                                                                new SubmissionBottomSheetActions.AsyncReportTask(
                                                                                submission,
                                                                                holder.itemView)
                                                                        .executeOnExecutor(
                                                                                AsyncTask
                                                                                        .THREAD_POOL_EXECUTOR,
                                                                                reportReason);
                                                            }
                                                        })
                                                .build();

                                final RadioGroup reasonGroup =
                                        reportDialog
                                                .getCustomView()
                                                .findViewById(R.id.report_reasons);

                                reasonGroup.setOnCheckedChangeListener(
                                        new RadioGroup.OnCheckedChangeListener() {
                                            @Override
                                            public void onCheckedChanged(
                                                    RadioGroup group, int checkedId) {
                                                if (checkedId == R.id.report_other)
                                                    reportDialog
                                                            .getCustomView()
                                                            .findViewById(R.id.input_report_reason)
                                                            .setVisibility(View.VISIBLE);
                                                else
                                                    reportDialog
                                                            .getCustomView()
                                                            .findViewById(R.id.input_report_reason)
                                                            .setVisibility(View.GONE);
                                            }
                                        });

                                // Load sub's report reasons and show the appropriate ones
                                new AsyncTask<Void, Void, Ruleset>() {
                                    @Override
                                    protected Ruleset doInBackground(Void... voids) {
                                        return Authentication.reddit.getRules(
                                                submission.getSubredditName());
                                    }

                                    @Override
                                    protected void onPostExecute(Ruleset rules) {
                                        reportDialog
                                                .getCustomView()
                                                .findViewById(R.id.report_loading)
                                                .setVisibility(View.GONE);
                                        if (rules.getSubredditRules().size() > 0) {
                                            TextView subHeader = new TextView(mContext);
                                            subHeader.setText(
                                                    mContext.getString(
                                                            R.string.report_sub_rules,
                                                            submission.getSubredditName()));
                                            reasonGroup.addView(
                                                    subHeader, reasonGroup.getChildCount() - 2);
                                        }
                                        for (SubredditRule rule : rules.getSubredditRules()) {
                                            if (rule.getKind() == SubredditRule.RuleKind.LINK
                                                    || rule.getKind()
                                                            == SubredditRule.RuleKind.ALL) {
                                                RadioButton btn = new RadioButton(mContext);
                                                btn.setText(rule.getViolationReason());
                                                reasonGroup.addView(
                                                        btn, reasonGroup.getChildCount() - 2);
                                                btn.getLayoutParams().width =
                                                        WindowManager.LayoutParams.MATCH_PARENT;
                                            }
                                        }
                                        if (rules.getSiteRules().size() > 0) {
                                            TextView siteHeader = new TextView(mContext);
                                            siteHeader.setText(R.string.report_site_rules);
                                            reasonGroup.addView(
                                                    siteHeader, reasonGroup.getChildCount() - 2);
                                        }
                                        for (String rule : rules.getSiteRules()) {
                                            RadioButton btn = new RadioButton(mContext);
                                            btn.setText(rule);
                                            reasonGroup.addView(
                                                    btn, reasonGroup.getChildCount() - 2);
                                            btn.getLayoutParams().width =
                                                    WindowManager.LayoutParams.MATCH_PARENT;
                                        }
                                    }
                                }.execute();

                                reportDialog.show();
                                break;
                            case 8:
                                if (SettingValues.shareLongLink) {
                                    Reddit.defaultShareText(
                                            submission.getTitle(),
                                            "https://reddit.com" + submission.getPermalink(),
                                            mContext);
                                } else {
                                    Reddit.defaultShareText(
                                            submission.getTitle(),
                                            "https://reddit.com/comments/" + submission.getId(),
                                            mContext);
                                }
                                break;
                            case 6:
                                {
                                    ClipboardUtil.copyToClipboard(
                                            mContext, "Link", submission.getUrl());
                                    Toast.makeText(
                                                    mContext,
                                                    R.string.submission_link_copied,
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                                break;
                            case 25:
                                final TextView showText = new TextView(mContext);
                                showText.setText(
                                        StringEscapeUtils.unescapeHtml4(
                                                submission.getTitle()
                                                        + "\n\n"
                                                        + submission.getSelftext()));
                                showText.setTextIsSelectable(true);
                                int sixteen = DisplayUtil.dpToPxVertical(24);
                                showText.setPadding(sixteen, 0, sixteen, 0);
                                new AlertDialog.Builder(mContext)
                                        .setView(showText)
                                        .setTitle("Select text to copy")
                                        .setCancelable(true)
                                        .setPositiveButton(
                                                "COPY SELECTED",
                                                (dialog13, which13) -> {
                                                    String selected =
                                                            showText.getText()
                                                                    .toString()
                                                                    .substring(
                                                                            showText
                                                                                    .getSelectionStart(),
                                                                            showText
                                                                                    .getSelectionEnd());
                                                    if (!selected.isEmpty()) {
                                                        ClipboardUtil.copyToClipboard(
                                                                mContext, "Selftext", selected);
                                                    } else {
                                                        ClipboardUtil.copyToClipboard(
                                                                mContext,
                                                                "Selftext",
                                                                CompatUtil.fromHtml(
                                                                        submission.getTitle()
                                                                                + "\n\n"
                                                                                + submission
                                                                                        .getSelftext()));
                                                    }
                                                    Toast.makeText(
                                                                    mContext,
                                                                    R.string
                                                                            .submission_comment_copied,
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                })
                                        .setNegativeButton(R.string.btn_cancel, null)
                                        .setNeutralButton(
                                                "COPY ALL",
                                                (dialog14, which14) -> {
                                                    ClipboardUtil.copyToClipboard(
                                                            mContext,
                                                            "Selftext",
                                                            StringEscapeUtils.unescapeHtml4(
                                                                    submission.getTitle()
                                                                            + "\n\n"
                                                                            + submission
                                                                                    .getSelftext()));

                                                    Toast.makeText(
                                                                    mContext,
                                                                    R.string.submission_text_copied,
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                })
                                        .show();
                                break;
                        }
                    }
                });
        b.show();
    }

    private void saveSubmission(
            final Submission submission,
            final Activity mContext,
            final SubmissionViewHolder holder,
            final boolean full) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ActionStates.isSaved(submission)) {
                        new AccountManager(Authentication.reddit).unsave(submission);
                        ActionStates.setSaved(submission, false);
                    } else {
                        new AccountManager(Authentication.reddit).save(submission);
                        ActionStates.setSaved(submission, true);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Snackbar s;
                try {
                    if (ActionStates.isSaved(submission)) {

                        BlendModeUtil.tintImageViewAsSrcAtop(
                                (ImageView) holder.save,
                                ContextCompat.getColor(mContext, R.color.md_amber_500));
                        holder.save.setContentDescription(mContext.getString(R.string.btn_unsave));
                        s =
                                Snackbar.make(
                                        holder.itemView,
                                        R.string.submission_info_saved,
                                        Snackbar.LENGTH_LONG);
                        if (Authentication.me.hasGold()) {
                            s.setAction(
                                    R.string.category_categorize,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            categorizeSaved(submission, holder.itemView, mContext);
                                        }
                                    });
                        }

                        AnimatorUtil.setFlashAnimation(
                                holder.itemView,
                                holder.save,
                                ContextCompat.getColor(mContext, R.color.md_amber_500));
                    } else {
                        s =
                                Snackbar.make(
                                        holder.itemView,
                                        R.string.submission_info_unsaved,
                                        Snackbar.LENGTH_SHORT);
                        final int getTintColor =
                                holder.itemView.getTag(holder.itemView.getId()) != null
                                                        && holder.itemView
                                                                .getTag(holder.itemView.getId())
                                                                .equals("none")
                                                || full
                                        ? Palette.getCurrentTintColor(mContext)
                                        : Palette.getWhiteTintColor();
                        BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.save, getTintColor);
                        holder.save.setContentDescription(mContext.getString(R.string.btn_save));
                    }
                    LayoutUtils.showSnackbar(s);
                } catch (Exception ignored) {

                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void categorizeSaved(
            final Submission submission, View itemView, final Context mContext) {
        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... voids) {
                // TODO: implement categorizeSaved logic
                return null;
            }

            @Override
            protected void onPostExecute(List<String> result) {
                // TODO: handle result
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public <T extends Contribution> void populateSubmissionViewHolder(
            final SubmissionViewHolder holder,
            final Submission submission,
            final Activity mContext,
            boolean fullscreen,
            final boolean full,
            final List<T> posts,
            final RecyclerView recyclerview,
            final boolean same,
            final boolean offline,
            final String baseSub,
            @Nullable final CommentAdapter adapter) {
        holder.itemView.findViewById(R.id.vote).setVisibility(View.GONE);

        if (!offline
                && UserSubscriptions.modOf != null
                && submission.getSubredditName() != null
                && UserSubscriptions.modOf.contains(
                        submission.getSubredditName().toLowerCase(Locale.ENGLISH))) {
            holder.mod.setVisibility(View.VISIBLE);
            final Map<String, Integer> reports = submission.getUserReports();
            final Map<String, String> reports2 = submission.getModeratorReports();
            if (reports.size() + reports2.size() > 0) {
                BlendModeUtil.tintImageViewAsSrcAtop(
                        (ImageView) holder.mod,
                        ContextCompat.getColor(mContext, R.color.md_red_300));
            } else {
                final int getTintColor =
                        holder.itemView.getTag(holder.itemView.getId()) != null
                                                && holder.itemView
                                                        .getTag(holder.itemView.getId())
                                                        .equals("none")
                                        || full
                                ? Palette.getCurrentTintColor(mContext)
                                : Palette.getWhiteTintColor();
                BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.mod, getTintColor);
            }
            holder.mod.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            SubmissionModActions.showModBottomSheet(
                                    mContext,
                                    submission,
                                    posts,
                                    holder,
                                    recyclerview,
                                    reports,
                                    reports2);
                        }
                    });
        } else {
            holder.mod.setVisibility(View.GONE);
        }

        holder.menu.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SubmissionBottomSheetActions.showBottomSheet(
                                mContext, submission, holder, posts, baseSub, recyclerview, full);
                    }
                });

        // Use this to offset the submission score
        int submissionScore = submission.getScore();

        final int commentCount = submission.getCommentCount();
        final int more = LastComments.commentsSince(submission);
        holder.comments.setText(
                String.format(
                        Locale.getDefault(),
                        "%d %s",
                        commentCount,
                        ((more > 0 && SettingValues.commentLastVisit) ? "(+" + more + ")" : "")));
        String scoreRatio =
                (SettingValues.upvotePercentage && full && submission.getUpvoteRatio() != null)
                        ? "(" + (int) (submission.getUpvoteRatio() * 100) + "%)"
                        : "";

        if (!scoreRatio.isEmpty()) {
            TextView percent = holder.itemView.findViewById(R.id.percent);
            percent.setVisibility(View.VISIBLE);
            percent.setText(scoreRatio);

            final double numb = (submission.getUpvoteRatio());
            if (numb <= .5) {
                if (numb <= .1) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_500));
                } else if (numb <= .3) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_400));
                } else {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_300));
                }
            } else {
                if (numb >= .9) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_500));
                } else if (numb >= .7) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_400));
                } else {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_300));
                }
            }
        }

        final ImageView downvotebutton = (ImageView) holder.downvote;
        final ImageView upvotebutton = (ImageView) holder.upvote;

        if (submission.isArchived()) {
            downvotebutton.setVisibility(View.GONE);
            upvotebutton.setVisibility(View.GONE);
        } else if (Authentication.isLoggedIn && Authentication.didOnline) {
            if (SettingValues.actionbarVisible && downvotebutton.getVisibility() != View.VISIBLE) {
                downvotebutton.setVisibility(View.VISIBLE);
                upvotebutton.setVisibility(View.VISIBLE);
            }
        }

        // Set the colors and styles for the score text depending on what state it is in
        // Also set content descriptions
        switch (ActionStates.getVoteDirection(submission)) {
            case UPVOTE:
                {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_orange_500));
                    BlendModeUtil.tintImageViewAsSrcAtop(
                            upvotebutton, ContextCompat.getColor(mContext, R.color.md_orange_500));
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvoted));
                    holder.score.setTypeface(null, Typeface.BOLD);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    BlendModeUtil.tintImageViewAsSrcAtop(downvotebutton, getTintColor);
                    downvotebutton.setContentDescription(mContext.getString(R.string.btn_downvote));
                    if (submission.getVote() != VoteDirection.UPVOTE) {
                        if (submission.getVote() == VoteDirection.DOWNVOTE) ++submissionScore;
                        ++submissionScore; // offset the score by +1
                    }
                    break;
                }
            case DOWNVOTE:
                {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_blue_500));
                    BlendModeUtil.tintImageViewAsSrcAtop(
                            downvotebutton, ContextCompat.getColor(mContext, R.color.md_blue_500));
                    downvotebutton.setContentDescription(
                            mContext.getString(R.string.btn_downvoted));
                    holder.score.setTypeface(null, Typeface.BOLD);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    BlendModeUtil.tintImageViewAsSrcAtop(upvotebutton, getTintColor);
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvote));
                    if (submission.getVote() != VoteDirection.DOWNVOTE) {
                        if (submission.getVote() == VoteDirection.UPVOTE) --submissionScore;
                        --submissionScore; // offset the score by +1
                    }
                    break;
                }
            case NO_VOTE:
                {
                    holder.score.setTextColor(holder.comments.getCurrentTextColor());
                    holder.score.setTypeface(null, Typeface.NORMAL);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    final List<ImageView> imageViewSet =
                            Arrays.asList(downvotebutton, upvotebutton);
                    BlendModeUtil.tintImageViewsAsSrcAtop(imageViewSet, getTintColor);
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvote));
                    downvotebutton.setContentDescription(mContext.getString(R.string.btn_downvote));
                    break;
                }
        }

        // if the submission is already at 0pts, keep it at 0pts
        submissionScore = Math.max(submissionScore, 0);
        if (submissionScore >= 10000 && SettingValues.abbreviateScores) {
            holder.score.setText(
                    String.format(
                            Locale.getDefault(), "%.1fk", (((double) submissionScore) / 1000)));
        } else {
            holder.score.setText(String.format(Locale.getDefault(), "%d", submissionScore));
        }

        // Save the score so we can use it in the OnClickListeners for the vote buttons
        final int SUBMISSION_SCORE = submissionScore;

        final ImageView hideButton = (ImageView) holder.hide;
        if (hideButton != null) {
            if (SettingValues.hideButton && Authentication.isLoggedIn) {
                hideButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                SubmissionBottomSheetActions.hideSubmission(submission, posts, baseSub, recyclerview, mContext);
                            }
                        });
            } else {
                hideButton.setVisibility(View.GONE);
            }
        }
        if (Authentication.isLoggedIn && Authentication.didOnline) {
            if (ActionStates.isSaved(submission)) {
                BlendModeUtil.tintImageViewAsSrcAtop(
                        (ImageView) holder.save,
                        ContextCompat.getColor(mContext, R.color.md_amber_500));
                holder.save.setContentDescription(mContext.getString(R.string.btn_unsave));
            } else {
                final int getTintColor =
                        holder.itemView.getTag(holder.itemView.getId()) != null
                                                && holder.itemView
                                                        .getTag(holder.itemView.getId())
                                                        .equals("none")
                                        || full
                                ? Palette.getCurrentTintColor(mContext)
                                : Palette.getWhiteTintColor();
                BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.save, getTintColor);
                holder.save.setContentDescription(mContext.getString(R.string.btn_save));
            }
            holder.save.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            SubmissionBottomSheetActions.saveSubmission(submission, mContext, holder, full);
                        }
                    });
        }

        if (!SettingValues.saveButton && !full
                || !Authentication.isLoggedIn
                || !Authentication.didOnline) {
            holder.save.setVisibility(View.GONE);
        }

        ImageView thumbImage2 = ((ImageView) holder.thumbimage);

        if (holder.leadImage.thumbImage2 == null) {
            holder.leadImage.setThumbnail(thumbImage2);
        }

        final ContentType.Type type = ContentType.getContentType(submission);

        SubmissionClickActions.addClickFunctions(holder.leadImage, type, mContext, submission, holder, full);

        if (thumbImage2 != null) {
            SubmissionClickActions.addClickFunctions(thumbImage2, type, mContext, submission, holder, full);
        }

        if (full) {
            SubmissionClickActions.addClickFunctions(
                    holder.itemView.findViewById(R.id.wraparea),
                    type,
                    mContext,
                    submission,
                    holder,
                    full);
        }

        if (full) {
            holder.leadImage.setWrapArea(holder.itemView.findViewById(R.id.wraparea));
        }

        if (full
                && (submission.getDataNode() != null
                        && submission.getDataNode().has("crosspost_parent_list")
                        && submission.getDataNode().get("crosspost_parent_list") != null
                        && submission.getDataNode().get("crosspost_parent_list").get(0) != null)) {
            holder.itemView.findViewById(R.id.crosspost).setVisibility(View.VISIBLE);
            ((TextView) holder.itemView.findViewById(R.id.crossinfo))
                    .setText(SubmissionCache.getCrosspostLine(submission, mContext));
            ((Reddit) mContext.getApplicationContext())
                    .getImageLoader()
                    .displayImage(
                            submission
                                    .getDataNode()
                                    .get("crosspost_parent_list")
                                    .get(0)
                                    .get("thumbnail")
                                    .asText(),
                            ((ImageView) holder.itemView.findViewById(R.id.crossthumb)));
            holder.itemView
                    .findViewById(R.id.crosspost)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    OpenRedditLink.openUrl(
                                            mContext,
                                            submission
                                                    .getDataNode()
                                                    .get("crosspost_parent_list")
                                                    .get(0)
                                                    .get("permalink")
                                                    .asText(),
                                            true);
                                }
                            });
        }

        holder.leadImage.setSubmission(submission, full, baseSub, type);

        holder.itemView.setOnLongClickListener(
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {

                        if (offline) {
                            Snackbar s =
                                    Snackbar.make(
                                            holder.itemView,
                                            mContext.getString(R.string.offline_msg),
                                            Snackbar.LENGTH_SHORT);
                            LayoutUtils.showSnackbar(s);
                        } else {
                            if (SettingValues.actionbarTap && !full) {
                                CreateCardView.toggleActionbar(holder.itemView);
                            } else {
                                holder.itemView.findViewById(R.id.menu).callOnClick();
                            }
                        }
                        return true;
                    }
                });

        SubmissionModActions.doText(holder, submission, mContext, baseSub, full);

        if (!full
                && SettingValues.isSelftextEnabled(baseSub)
                && submission.isSelfPost()
                && !submission.getSelftext().isEmpty()
                && !submission.isNsfw()
                && !submission.getDataNode().get("spoiler").asBoolean()
                && !submission.getDataNode().get("selftext_html").asText().trim().isEmpty()) {
            holder.body.setVisibility(View.VISIBLE);
            String text = submission.getDataNode().get("selftext_html").asText();
            int typef = new FontPreferences(mContext).getFontTypeComment().getTypeface();
            Typeface typeface;
            if (typef >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(mContext, typef);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.body.setTypeface(typeface);

            holder.body.setTextHtml(
                    CompatUtil.fromHtml(
                                    text.substring(
                                            0,
                                            text.contains("\n")
                                                    ? text.indexOf("\n")
                                                    : text.length()))
                            .toString()
                            .replace("<sup>", "<sup><small>")
                            .replace("</sup>", "</small></sup>"),
                    "none ");
            holder.body.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            holder.itemView.callOnClick();
                        }
                    });
            holder.body.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            holder.menu.callOnClick();
                            return true;
                        }
                    });
        } else if (!full) {
            holder.body.setVisibility(View.GONE);
        }

        if (full) {
            if (!submission.getSelftext().isEmpty()) {
                int typef = new FontPreferences(mContext).getFontTypeComment().getTypeface();
                Typeface typeface;
                if (typef >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(mContext, typef);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                holder.firstTextView.setTypeface(typeface);

                setViews(
                        submission.getDataNode().get("selftext_html").asText(),
                        submission.getSubredditName() == null
                                ? "all"
                                : submission.getSubredditName(),
                        holder);
                holder.itemView.findViewById(R.id.body_area).setVisibility(View.VISIBLE);
            } else {
                holder.itemView.findViewById(R.id.body_area).setVisibility(View.GONE);
            }
        }

        try {
            final TextView points = holder.score;
            final TextView comments = holder.comments;

            if (Authentication.isLoggedIn && !offline && Authentication.didOnline) {
                {
                    downvotebutton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (SettingValues.storeHistory && !full) {
                                        if (!submission.isNsfw()
                                                || SettingValues.storeNSFWHistory) {
                                            HasSeen.addSeen(submission.getFullName());
                                            if (mContext instanceof MainActivity) {
                                                holder.title.setAlpha(0.54f);
                                                holder.body.setAlpha(0.54f);
                                            }
                                        }
                                    }
                                    final int getTintColor =
                                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                                    && holder.itemView
                                                                            .getTag(
                                                                                    holder.itemView
                                                                                            .getId())
                                                                            .equals("none")
                                                            || full
                                                    ? Palette.getCurrentTintColor(mContext)
                                                    : Palette.getWhiteTintColor();
                                    if (ActionStates.getVoteDirection(submission)
                                            != VoteDirection.DOWNVOTE) { // has not been downvoted
                                        points.setTextColor(
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton, getTintColor);
                                        downvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_downvoted));

                                        AnimatorUtil.setFlashAnimation(
                                                holder.itemView,
                                                downvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        holder.score.setTypeface(null, Typeface.BOLD);
                                        final int DOWNVOTE_SCORE =
                                                (SUBMISSION_SCORE == 0)
                                                        ? 0
                                                        : SUBMISSION_SCORE
                                                                - 1; // if a post is at 0 votes,
                                        // keep it at 0 when downvoting
                                        new Vote(false, points, mContext).execute(submission);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.DOWNVOTE);
                                    } else { // un-downvoted a post
                                        points.setTextColor(comments.getCurrentTextColor());
                                        new Vote(points, mContext).execute(submission);
                                        holder.score.setTypeface(null, Typeface.NORMAL);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.NO_VOTE);
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton, getTintColor);
                                        downvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_downvote));
                                    }
                                    setSubmissionScoreText(submission, holder);
                                    if (!full
                                            && !SettingValues.actionbarVisible
                                            && SettingValues.defaultCardView
                                                    != CreateCardView.CardEnum.DESKTOP) {
                                        CreateCardView.toggleActionbar(holder.itemView);
                                    }
                                }
                            });
                }
                {
                    upvotebutton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (SettingValues.storeHistory && !full) {
                                        if (!submission.isNsfw()
                                                || SettingValues.storeNSFWHistory) {
                                            HasSeen.addSeen(submission.getFullName());
                                            if (mContext instanceof MainActivity) {
                                                holder.title.setAlpha(0.54f);
                                                holder.body.setAlpha(0.54f);
                                            }
                                        }
                                    }

                                    final int getTintColor =
                                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                                    && holder.itemView
                                                                            .getTag(
                                                                                    holder.itemView
                                                                                            .getId())
                                                                            .equals("none")
                                                            || full
                                                    ? Palette.getCurrentTintColor(mContext)
                                                    : Palette.getWhiteTintColor();
                                    if (ActionStates.getVoteDirection(submission)
                                            != VoteDirection.UPVOTE) { // has not been upvoted
                                        points.setTextColor(
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton, getTintColor);
                                        upvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_upvoted));

                                        AnimatorUtil.setFlashAnimation(
                                                holder.itemView,
                                                upvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        holder.score.setTypeface(null, Typeface.BOLD);

                                        new Vote(true, points, mContext).execute(submission);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.UPVOTE);

                                    } else { // un-upvoted a post
                                        points.setTextColor(comments.getCurrentTextColor());
                                        new Vote(points, mContext).execute(submission);
                                        holder.score.setTypeface(null, Typeface.NORMAL);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.NO_VOTE);
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton, getTintColor);
                                        upvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_upvote));
                                    }
                                    setSubmissionScoreText(submission, holder);
                                    if (!full
                                            && !SettingValues.actionbarVisible
                                            && SettingValues.defaultCardView
                                                    != CreateCardView.CardEnum.DESKTOP) {
                                        CreateCardView.toggleActionbar(holder.itemView);
                                    }
                                }
                            });
                }
            } else {
                upvotebutton.setVisibility(View.GONE);
                downvotebutton.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        final View edit = holder.edit;

        if (Authentication.name != null
                && Authentication.name
                        .toLowerCase(Locale.ENGLISH)
                        .equals(submission.getAuthor().toLowerCase(Locale.ENGLISH))
                && Authentication.didOnline) {
            edit.setVisibility(View.VISIBLE);
            edit.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            new AsyncTask<Void, Void, ArrayList<String>>() {
                                List<FlairTemplate> flairlist;

                                @Override
                                protected ArrayList<String> doInBackground(Void... params) {
                                    FlairReference allFlairs =
                                            new FluentRedditClient(Authentication.reddit)
                                                    .subreddit(submission.getSubredditName())
                                                    .flair();
                                    try {
                                        flairlist = allFlairs.options(submission);
                                        final ArrayList<String> finalFlairs = new ArrayList<>();
                                        for (FlairTemplate temp : flairlist) {
                                            finalFlairs.add(temp.getText());
                                        }
                                        return finalFlairs;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        // sub probably has no flairs?
                                    }

                                    return null;
                                }

                                @Override
                                public void onPostExecute(final ArrayList<String> data) {
                                    final boolean flair = (data != null && !data.isEmpty());

                                    int[] attrs = new int[] {R.attr.tintColor};
                                    TypedArray ta = mContext.obtainStyledAttributes(attrs);

                                    final int color2 = ta.getColor(0, Color.WHITE);
                                    Drawable edit_drawable =
                                            mContext.getResources().getDrawable(R.drawable.ic_edit);
                                    Drawable nsfw_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_visibility_off);
                                    Drawable delete_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_delete);
                                    Drawable flair_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_text_fields);

                                    final List<Drawable> drawableSet =
                                            Arrays.asList(
                                                    edit_drawable,
                                                    nsfw_drawable,
                                                    delete_drawable,
                                                    flair_drawable);
                                    BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color2);

                                    ta.recycle();

                                    BottomSheet.Builder b =
                                            new BottomSheet.Builder(mContext)
                                                    .title(
                                                            CompatUtil.fromHtml(
                                                                    submission.getTitle()));

                                    if (submission.isSelfPost()) {
                                        b.sheet(
                                                1,
                                                edit_drawable,
                                                mContext.getString(R.string.edit_selftext));
                                    }
                                    if (submission.isNsfw()) {
                                        b.sheet(
                                                4,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_unmark_nsfw));
                                    } else {
                                        b.sheet(
                                                4,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_mark_nsfw));
                                    }
                                    if (submission.getDataNode().get("spoiler").asBoolean()) {
                                        b.sheet(
                                                5,
                                                nsfw_drawable,
                                                mContext.getString(
                                                        R.string.mod_btn_unmark_spoiler));
                                    } else {
                                        b.sheet(
                                                5,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_mark_spoiler));
                                    }

                                    b.sheet(
                                            2,
                                            delete_drawable,
                                            mContext.getString(R.string.delete_submission));

                                    if (flair) {
                                        b.sheet(
                                                3,
                                                flair_drawable,
                                                mContext.getString(R.string.set_submission_flair));
                                    }

                                    b.listener(
                                                    new DialogInterface.OnClickListener() {

                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialog, int which) {
                                                            switch (which) {
                                                                case 1:
                                                                    {
                                                                        LayoutInflater inflater =
                                                                                mContext
                                                                                        .getLayoutInflater();

                                                                        final View dialoglayout =
                                                                                inflater.inflate(
                                                                                        R.layout
                                                                                                .edit_comment,
                                                                                        null);

                                                                        final EditText e =
                                                                                dialoglayout
                                                                                        .findViewById(
                                                                                                R.id
                                                                                                        .entry);
                                                                        e.setText(
                                                                                StringEscapeUtils
                                                                                        .unescapeHtml4(
                                                                                                submission
                                                                                                        .getSelftext()));

                                                                        DoEditorActions.doActions(
                                                                                e,
                                                                                dialoglayout,
                                                                                ((AppCompatActivity)
                                                                                                mContext)
                                                                                        .getSupportFragmentManager(),
                                                                                mContext,
                                                                                null,
                                                                                null);

                                                                        final AlertDialog.Builder
                                                                                builder =
                                                                                        new AlertDialog
                                                                                                        .Builder(
                                                                                                        mContext)
                                                                                                .setCancelable(
                                                                                                        false)
                                                                                                .setView(
                                                                                                        dialoglayout);
                                                                        final Dialog d =
                                                                                builder.create();
                                                                        d.getWindow()
                                                                                .setSoftInputMode(
                                                                                        WindowManager
                                                                                                .LayoutParams
                                                                                                .SOFT_INPUT_ADJUST_RESIZE);

                                                                        d.show();
                                                                        dialoglayout
                                                                                .findViewById(
                                                                                        R.id.cancel)
                                                                                .setOnClickListener(
                                                                                        new View
                                                                                                .OnClickListener() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onClick(
                                                                                                            View
                                                                                                                    v) {
                                                                                                d
                                                                                                        .dismiss();
                                                                                            }
                                                                                        });
                                                                        dialoglayout
                                                                                .findViewById(
                                                                                        R.id.submit)
                                                                                .setOnClickListener(
                                                                                        new View
                                                                                                .OnClickListener() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onClick(
                                                                                                            View
                                                                                                                    v) {
                                                                                                final
                                                                                                String
                                                                                                        text =
                                                                                                                e.getText()
                                                                                                                        .toString();
                                                                                                new AsyncTask<
                                                                                                        Void,
                                                                                                        Void,
                                                                                                        Void>() {
                                                                                                    @Override
                                                                                                    protected
                                                                                                    Void
                                                                                                            doInBackground(
                                                                                                                    Void
                                                                                                                                    ...
                                                                                                                            params) {
                                                                                                        try {
                                                                                                            new AccountManager(
                                                                                                                            Authentication
                                                                                                                                    .reddit)
                                                                                                                    .updateContribution(
                                                                                                                            submission,
                                                                                                                            text);
                                                                                                            if (adapter
                                                                                                                    != null) {
                                                                                                                adapter
                                                                                                                        .dataSet
                                                                                                                        .reloadSubmission(
                                                                                                                                adapter);
                                                                                                            }
                                                                                                            d
                                                                                                                    .dismiss();
                                                                                                        } catch (
                                                                                                                Exception
                                                                                                                        e) {
                                                                                                            (mContext)
                                                                                                                    .runOnUiThread(
                                                                                                                            new Runnable() {
                                                                                                                                @Override
                                                                                                                                public
                                                                                                                                void
                                                                                                                                        run() {
                                                                                                                                    new AlertDialog
                                                                                                                                                    .Builder(
                                                                                                                                                    mContext)
                                                                                                                                            .setTitle(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .comment_delete_err)
                                                                                                                                            .setMessage(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .comment_delete_err_msg)
                                                                                                                                            .setPositiveButton(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .btn_yes,
                                                                                                                                                    (dialog1,
                                                                                                                                                            which1) -> {
                                                                                                                                                        dialog1
                                                                                                                                                                .dismiss();
                                                                                                                                                        doInBackground();
                                                                                                                                                    })
                                                                                                                                            .setNegativeButton(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .btn_no,
                                                                                                                                                    (dialog12,
                                                                                                                                                            which12) ->
                                                                                                                                                            dialog12
                                                                                                                                                                    .dismiss())
                                                                                                                                            .show();
                                                                                                                                }
                                                                                                                            });
                                                                                                        }
                                                                                                        return null;
                                                                                                    }

                                                                                                    @Override
                                                                                                    protected
                                                                                                    void
                                                                                                            onPostExecute(
                                                                                                                    Void
                                                                                                                            aVoid) {
                                                                                                        if (adapter
                                                                                                                != null) {
                                                                                                            adapter
                                                                                                                    .notifyItemChanged(
                                                                                                                            1);
                                                                                                        }
                                                                                                    }
                                                                                                }.executeOnExecutor(
                                                                                                        AsyncTask
                                                                                                                .THREAD_POOL_EXECUTOR);
                                                                                            }
                                                                                        });
                                                                    }
                                                                    break;
                                                                case 2:
                                                                    {
                                                                        new AlertDialog.Builder(
                                                                                        mContext)
                                                                                .setTitle(
                                                                                        R.string
                                                                                                .really_delete_submission)
                                                                                .setPositiveButton(
                                                                                        R.string
                                                                                                .btn_yes,
                                                                                        (dialog13,
                                                                                                which13) ->
                                                                                                new AsyncTask<
                                                                                                        Void,
                                                                                                        Void,
                                                                                                        Void>() {
                                                                                                    @Override
                                                                                                    protected
                                                                                                    Void
                                                                                                            doInBackground(
                                                                                                                    Void
                                                                                                                                    ...
                                                                                                                            params) {
                                                                                                        try {
                                                                                                            new ModerationManager(
                                                                                                                            Authentication
                                                                                                                                    .reddit)
                                                                                                                    .delete(
                                                                                                                            submission);
                                                                                                        } catch (
                                                                                                                ApiException
                                                                                                                        e) {
                                                                                                            e
                                                                                                                    .printStackTrace();
                                                                                                        }
                                                                                                        return null;
                                                                                                    }

                                                                                                    @Override
                                                                                                    protected
                                                                                                    void
                                                                                                            onPostExecute(
                                                                                                                    Void
                                                                                                                            aVoid) {
                                                                                                        (mContext)
                                                                                                                .runOnUiThread(
                                                                                                                        new Runnable() {
                                                                                                                            @Override
                                                                                                                            public
                                                                                                                            void
                                                                                                                                    run() {
                                                                                                                                (holder.title)
                                                                                                                                        .setTextHtml(
                                                                                                                                                mContext
                                                                                                                                                        .getString(
                                                                                                                                                                R
                                                                                                                                                                        .string
                                                                                                                                                                        .content_deleted));
                                                                                                                                if (holder.firstTextView
                                                                                                                                        != null) {
                                                                                                                                    holder
                                                                                                                                            .firstTextView
                                                                                                                                            .setText(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .content_deleted);
                                                                                                                                    holder
                                                                                                                                            .commentOverflow
                                                                                                                                            .setVisibility(
                                                                                                                                                    View
                                                                                                                                                            .GONE);
                                                                                                                                } else {
                                                                                                                                    if (holder
                                                                                                                                                    .itemView
                                                                                                                                                    .findViewById(
                                                                                                                                                            R
                                                                                                                                                                    .id
                                                                                                                                                                    .body)
                                                                                                                                            != null) {
                                                                                                                                        ((TextView)
                                                                                                                                                        holder
                                                                                                                                                                .itemView
                                                                                                                                                                .findViewById(
                                                                                                                                                                        R
                                                                                                                                                                                .id
                                                                                                                                                                                .body))
                                                                                                                                                .setText(
                                                                                                                                                        R
                                                                                                                                                                .string
                                                                                                                                                                .content_deleted);
                                                                                                                                    }
                                                                                                                                }
                                                                                                                            }
                                                                                                                        });
                                                                                                    }
                                                                                                }.executeOnExecutor(
                                                                                                        AsyncTask
                                                                                                                .THREAD_POOL_EXECUTOR))
                                                                                .setNegativeButton(
                                                                                        R.string
                                                                                                .btn_cancel,
                                                                                        null)
                                                                                .show();
                                                                    }
                                                                    break;
                                                                case 3:
                                                                    {
                                                                        new MaterialDialog.Builder(
                                                                                        mContext)
                                                                                .items(data)
                                                                                .title(
                                                                                        R.string
                                                                                                .sidebar_select_flair)
                                                                                .itemsCallback(
                                                                                        new MaterialDialog
                                                                                                .ListCallback() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onSelection(
                                                                                                            MaterialDialog
                                                                                                                    dialog,
                                                                                                            View
                                                                                                                    itemView,
                                                                                                            int
                                                                                                                    which,
                                                                                                            CharSequence
                                                                                                                    text) {
                                                                                                final
                                                                                                FlairTemplate
                                                                                                        t =
                                                                                                                flairlist
                                                                                                                        .get(which);
                                                                                                if (t
                                                                                                        .isTextEditable()) {
                                                                                                    new MaterialDialog
                                                                                                                    .Builder(
                                                                                                                    mContext)
                                                                                                            .title(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .mod_btn_submission_flair_text)
                                                                                                            .input(
                                                                                                                    mContext
                                                                                                                            .getString(
                                                                                                                                    R
                                                                                                                                            .string
                                                                                                                                            .mod_flair_hint),
                                                                                                                    t
                                                                                                                            .getText(),
                                                                                                                    true,
                                                                                                                    (dialog14,
                                                                                                                            input) -> {})
                                                                                                            .positiveText(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .btn_set)
                                                                                                            .onPositive(
                                                                                                                    new MaterialDialog
                                                                                                                            .SingleButtonCallback() {
                                                                                                                        @Override
                                                                                                                        public
                                                                                                                        void
                                                                                                                                onClick(
                                                                                                                                        MaterialDialog
                                                                                                                                                dialog,
                                                                                                                                        DialogAction
                                                                                                                                                which) {
                                                                                                                            final
                                                                                                                            String
                                                                                                                                    flair =
                                                                                                                                            dialog.getInputEditText()
                                                                                                                                                    .getText()
                                                                                                                                                    .toString();
                                                                                                                            new AsyncTask<
                                                                                                                                    Void,
                                                                                                                                    Void,
                                                                                                                                    Boolean>() {
                                                                                                                                @Override
                                                                                                                                protected
                                                                                                                                Boolean
                                                                                                                                        doInBackground(
                                                                                                                                                Void
                                                                                                                                                                ...
                                                                                                                                                        params) {
                                                                                                                                    try {
                                                                                                                                        new ModerationManager(
                                                                                                                                                        Authentication
                                                                                                                                                                .reddit)
                                                                                                                                                .setFlair(
                                                                                                                                                        submission
                                                                                                                                                                .getSubredditName(),
                                                                                                                                                        t,
                                                                                                                                                        flair,
                                                                                                                                                        submission);
                                                                                                                                        return true;
                                                                                                                                    } catch (
                                                                                                                                            ApiException
                                                                                                                                                    e) {
                                                                                                                                        e
                                                                                                                                                .printStackTrace();
                                                                                                                                        return false;
                                                                                                                                    }
                                                                                                                                }

                                                                                                                                @Override
                                                                                                                                protected
                                                                                                                                void
                                                                                                                                        onPostExecute(
                                                                                                                                                Boolean
                                                                                                                                                        done) {
                                                                                                                                    Snackbar
                                                                                                                                            s =
                                                                                                                                                    null;
                                                                                                                                    if (done) {
                                                                                                                                        if (holder.itemView
                                                                                                                                                != null) {
                                                                                                                                            s =
                                                                                                                                                    Snackbar
                                                                                                                                                            .make(
                                                                                                                                                                    holder.itemView,
                                                                                                                                                                    R
                                                                                                                                                                            .string
                                                                                                                                                                            .snackbar_flair_success,
                                                                                                                                                                    Snackbar
                                                                                                                                                                            .LENGTH_SHORT);
                                                                                                                                            SubmissionCache
                                                                                                                                                    .updateTitleFlair(
                                                                                                                                                            submission,
                                                                                                                                                            flair,
                                                                                                                                                            mContext);
                                                                                                                                            holder
                                                                                                                                                    .title
                                                                                                                                                    .setText(
                                                                                                                                                            SubmissionCache
                                                                                                                                                                    .getTitleLine(
                                                                                                                                                                            submission,
                                                                                                                                                                            mContext));
                                                                                                                                            // Force the title view to re-measure itself
                                                                                                                                            holder.title.requestLayout();
                                                                                                                                        }
                                                                                                                                    } else {
                                                                                                                                        if (holder.itemView
                                                                                                                                                != null) {
                                                                                                                                            s =
                                                                                                                                                    Snackbar
                                                                                                                                                            .make(
                                                                                                                                                                    holder.itemView,
                                                                                                                                                                    R
                                                                                                                                                                            .string
                                                                                                                                                                            .snackbar_flair_error,
                                                                                                                                                                    Snackbar
                                                                                                                                                                            .LENGTH_SHORT);
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    if (s
                                                                                                                                            != null) {
                                                                                                                                        LayoutUtils
                                                                                                                                                .showSnackbar(
                                                                                                                                                        s);
                                                                                                                                    }
                                                                                                                                }
                                                                                                                            }.executeOnExecutor(
                                                                                                                                    AsyncTask
                                                                                                                                            .THREAD_POOL_EXECUTOR);
                                                                                                                        }
                                                                                                                    })
                                                                                                            .negativeText(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .btn_cancel)
                                                                                                            .show();
                                                                                                } else {
                                                                                                    new AsyncTask<
                                                                                                            Void,
                                                                                                            Void,
                                                                                                            Boolean>() {
                                                                                                        @Override
                                                                                                        protected
                                                                                                        Boolean
                                                                                                                doInBackground(
                                                                                                                        Void
                                                                                                                                        ...
                                                                                                                                params) {
                                                                                                            try {
                                                                                                                new ModerationManager(
                                                                                                                                Authentication
                                                                                                                                        .reddit)
                                                                                                                        .setFlair(
                                                                                                                                submission
                                                                                                                                        .getSubredditName(),
                                                                                                                                t,
                                                                                                                                null,
                                                                                                                                submission);
                                                                                                                return true;
                                                                                                            } catch (
                                                                                                                    ApiException
                                                                                                                            e) {
                                                                                                                e
                                                                                                                        .printStackTrace();
                                                                                                                return false;
                                                                                                            }
                                                                                                        }

                                                                                                        @Override
                                                                                                        protected
                                                                                                        void
                                                                                                                onPostExecute(
                                                                                                                        Boolean
                                                                                                                                done) {
                                                                                                            Snackbar
                                                                                                                    s =
                                                                                                                            null;
                                                                                                            if (done) {
                                                                                                                if (holder.itemView
                                                                                                                        != null) {
                                                                                                                    s =
                                                                                                                            Snackbar
                                                                                                                                    .make(
                                                                                                                                            holder.itemView,
                                                                                                                                            R
                                                                                                                                                    .string
                                                                                                                                                    .snackbar_flair_success,
                                                                                                                                            Snackbar
                                                                                                                                                    .LENGTH_SHORT);
                                                                                                                    SubmissionCache
                                                                                                                            .updateTitleFlair(
                                                                                                                                    submission,
                                                                                                                                    t
                                                                                                                                            .getCssClass(),
                                                                                                                                    mContext);
                                                                                                                    holder
                                                                                                                            .title
                                                                                                                            .setText(
                                                                                                                                    SubmissionCache
                                                                                                                                            .getTitleLine(
                                                                                                                                                    submission,
                                                                                                                                                    mContext));
                                                                                                                    // Force the title view to re-measure itself
                                                                                                                    holder.title.requestLayout();
                                                                                                                }
                                                                                                            } else {
                                                                                                                if (holder.itemView
                                                                                                                        != null) {
                                                                                                                    s =
                                                                                                                            Snackbar
                                                                                                                                    .make(
                                                                                                                                            holder.itemView,
                                                                                                                                            R
                                                                                                                                                    .string
                                                                                                                                                    .snackbar_flair_error,
                                                                                                                                            Snackbar
                                                                                                                                                    .LENGTH_SHORT);
                                                                                                                }
                                                                                                            }
                                                                                                            if (s
                                                                                                                    != null) {
                                                                                                                LayoutUtils
                                                                                                                        .showSnackbar(
                                                                                                                                s);
                                                                                                            }
                                                                                                        }
                                                                                                    }.executeOnExecutor(
                                                                                                            AsyncTask
                                                                                                                    .THREAD_POOL_EXECUTOR);
                                                                                                }
                                                                                            }
                                                                                        })
                                                                                .show();
                                                                    }
                                                                    break;
                                                                case 4:
                                                                    if (submission.isNsfw()) {
                                                                        SubmissionModActions.unNsfwSubmission(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    } else {
                                                                        SubmissionModActions.setPostNsfw(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    }
                                                                    break;
                                                                case 5:
                                                                    if (submission
                                                                            .getDataNode()
                                                                            .get("spoiler")
                                                                            .asBoolean()) {
                                                                        SubmissionModActions.unSpoiler(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    } else {
                                                                        SubmissionModActions.setSpoiler(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    }
                                                                    break;
                                                            }
                                                        }
                                                    })
                                            .show();
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    });
        } else {
            edit.setVisibility(View.GONE);
        }

        if (HasSeen.getSeen(submission) && !full) {
            holder.title.setAlpha(0.54f);
            holder.body.setAlpha(0.54f);
        } else {
            holder.title.setAlpha(1f);
            if (!full) {
                holder.body.setAlpha(1f);
            }
        }
    }

    private void setSubmissionScoreText(Submission submission, SubmissionViewHolder holder) {
        int submissionScore = submission.getScore();
        switch (ActionStates.getVoteDirection(submission)) {
            case UPVOTE:
                {
                    if (submission.getVote() != VoteDirection.UPVOTE) {
                        if (submission.getVote() == VoteDirection.DOWNVOTE) ++submissionScore;
                        ++submissionScore; // offset the score by +1
                    }
                    break;
                }
            case DOWNVOTE:
                {
                    if (submission.getVote() != VoteDirection.DOWNVOTE) {
                        if (submission.getVote() == VoteDirection.UPVOTE) --submissionScore;
                        --submissionScore; // offset the score by +1
                    }
                    break;
                }
            case NO_VOTE:
                if (submission.getVote() == VoteDirection.UPVOTE
                        && submission.getAuthor().equalsIgnoreCase(Authentication.name)) {
                    submissionScore--;
                }
                break;
        }

        // if the submission is already at 0pts, keep it at 0pts
        submissionScore = Math.max(submissionScore, 0);
        if (submissionScore >= 10000 && SettingValues.abbreviateScores) {
            holder.score.setText(
                    String.format(
                            Locale.getDefault(), "%.1fk", (((double) submissionScore) / 1000)));
        } else {
            holder.score.setText(String.format(Locale.getDefault(), "%d", submissionScore));
        }
    }

    private void setViews(String rawHTML, String subredditName, SubmissionViewHolder holder) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        if (!blocks.get(0).startsWith("<table>") && !blocks.get(0).startsWith("<pre>")) {
            holder.firstTextView.setTextHtml(blocks.get(0), subredditName);
            startIndex = 1;
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                holder.commentOverflow.setViews(blocks, subredditName);
            } else {
                holder.commentOverflow.setViews(
                        blocks.subList(startIndex, blocks.size()), subredditName);
            }
        }
    }

}