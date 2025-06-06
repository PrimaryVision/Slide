package me.edgan.redditslide.Adapters;

/** Created by ccrama on 3/22/2015. */
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.devspark.robototextview.RobotoTypefaces;
import com.lusfold.androidkeyvaluestore.KVStore;
import com.mikepenz.itemanimators.AlphaInAnimator;
import com.mikepenz.itemanimators.SlideRightAlphaAnimator;
import com.nostra13.universalimageloader.utils.DiskCacheUtils;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Drafts;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.ImageFlairs;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionViews.PopulateSubmissionViewHolder;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Views.PreCachingLayoutManagerComments;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.AnimatorUtil;
import me.edgan.redditslide.util.DisplayUtil;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.CommentStateUtil;
import me.edgan.redditslide.util.AsyncLoadMoreTask;

import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int HEADER = 1;
    private final int SPACER = 6;
    public final Bitmap[] awardIcons;
    public Context mContext;
    public SubmissionComments dataSet;
    public Submission submission;
    public CommentViewHolder currentlySelected;
    public CommentNode currentNode;
    public String currentSelectedItem = "";
    public int shiftFrom;
    public FragmentManager fm;
    public int clickpos;
    public int currentPos;
    public CommentViewHolder isHolder;
    public boolean isClicking;
    public HashMap<String, Integer> keys = new HashMap<>();
    public ArrayList<CommentObject> currentComments;
    public ArrayList<String> deleted = new ArrayList<>();
    RecyclerView listView;
    public CommentPage mPage;
    int shifted;
    int toShiftTo;
    HashSet<String> hidden;
    public ArrayList<String> hiddenPersons;
    public ArrayList<String> toCollapse;
    public String backedText = "";
    public String currentlyEditingId = "";
    public SubmissionViewHolder submissionViewHolder;
    long lastSeen = 0;
    public ArrayList<String> approved = new ArrayList<>();
    public ArrayList<String> removed = new ArrayList<>();

    public CommentAdapter(
            CommentPage mContext,
            SubmissionComments dataSet,
            RecyclerView listView,
            Submission submission,
            FragmentManager fm) {
        this.mContext = mContext.getContext();
        mPage = mContext;
        this.listView = listView;
        this.dataSet = dataSet;
        this.fm = fm;

        this.submission = submission;
        hidden = new HashSet<>();
        currentComments = dataSet.comments;
        if (currentComments != null) {
            for (int i = 0; i < currentComments.size(); i++) {
                keys.put(currentComments.get(i).getName(), i);
            }
        }
        hiddenPersons = new ArrayList<>();
        toCollapse = new ArrayList<>();

        shifted = 0;

        // As per reddit API gids: 0=silver, 1=gold, 2=platinum
        awardIcons =
                new Bitmap[] {
                    BitmapFactory.decodeResource(mContext.getResources(), R.drawable.silver),
                    BitmapFactory.decodeResource(mContext.getResources(), R.drawable.gold),
                    BitmapFactory.decodeResource(mContext.getResources(), R.drawable.platinum),
                };
    }

    public void reset(
            Context mContext,
            SubmissionComments dataSet,
            RecyclerView listView,
            Submission submission,
            boolean reset) {

        doTimes();

        this.mContext = mContext;
        this.listView = listView;
        this.dataSet = dataSet;

        this.submission = submission;
        hidden = new HashSet<>();
        currentComments = dataSet.comments;
        if (currentComments != null) {
            for (int i = 0; i < currentComments.size(); i++) {
                keys.put(currentComments.get(i).getName(), i);
            }
        }

        hiddenPersons = new ArrayList<>();
        toCollapse = new ArrayList<>();

        if (currentSelectedItem != null && !currentSelectedItem.isEmpty() && !reset) {
            notifyDataSetChanged();
        } else {
            if (currentComments != null && !reset) {
                notifyItemRangeChanged(2, currentComments.size() + 1);
            } else if (currentComments == null) {
                currentComments = new ArrayList<>();
                notifyDataSetChanged();
            } else {
                notifyDataSetChanged();
            }
        }

        if (currentSelectedItem != null
                && !currentSelectedItem.isEmpty()
                && currentComments != null
                && !currentComments.isEmpty()) {
            int i = 2;
            for (CommentObject n : currentComments) {
                if (n instanceof CommentItem
                        && n.comment.getComment().getFullName().contains(currentSelectedItem)) {
                    ((PreCachingLayoutManagerComments) listView.getLayoutManager())
                            .scrollToPositionWithOffset(i, mPage.headerHeight);
                    break;
                }
                i++;
            }
            mPage.resetScroll(true);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        switch (i) {
            case SPACER:
                {
                    View v =
                            LayoutInflater.from(viewGroup.getContext())
                                    .inflate(R.layout.spacer_post, viewGroup, false);
                    return new SpacerViewHolder(v);
                }
            case HEADER:
                {
                    View v =
                            LayoutInflater.from(viewGroup.getContext())
                                    .inflate(R.layout.submission_fullscreen, viewGroup, false);
                    return new SubmissionViewHolder(v);
                }
            case 2:
                {
                    View v =
                            LayoutInflater.from(viewGroup.getContext())
                                    .inflate(R.layout.comment, viewGroup, false);
                    return new CommentViewHolder(v);
                }
            default:
                {
                    View v =
                            LayoutInflater.from(viewGroup.getContext())
                                    .inflate(R.layout.morecomment, viewGroup, false);
                    return new MoreCommentViewHolder(v);
                }
        }
    }

    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void expandAll() {
        if (currentComments == null) return;
        for (CommentObject o : currentComments) {
            if (o.comment.isTopLevel()) {
                hiddenPersons.remove(o.comment.getComment().getFullName());
                unhideAll(o.comment);
            }
        }
        notifyItemChanged(2);
    }

    public void collapseAll() {
        if (currentComments == null) return;
        for (CommentObject o : currentComments) {
            if (o.comment.isTopLevel()) {
                if (!hiddenPersons.contains(o.comment.getComment().getFullName())) {
                    hiddenPersons.add(o.comment.getComment().getFullName());
                }
                hideAll(o.comment);
            }
        }
        notifyItemChanged(2);
    }

    public void doScoreText(CommentViewHolder holder, Comment comment, CommentAdapter adapter) {
        holder.content.setText(
                CommentAdapterHelper.getScoreString(
                        comment, mContext, holder, submission, adapter));
    }

    public void doTimes() {
        if (submission != null
                && SettingValues.commentLastVisit
                && !dataSet.single
                && (SettingValues.storeHistory
                        && (!submission.isNsfw() || SettingValues.storeNSFWHistory))) {
            lastSeen = HasSeen.getSeenTime(submission);
            String fullname = submission.getFullName();
            if (fullname.contains("t3_")) {
                fullname = fullname.substring(3);
            }
            HasSeen.seenTimes.put(fullname, System.currentTimeMillis());
            KVStore.getInstance().insert(fullname, String.valueOf(System.currentTimeMillis()));
        }
        if (submission != null) {
            if (SettingValues.storeHistory) {
                if (submission.isNsfw() && !SettingValues.storeNSFWHistory) {
                } else {
                    HasSeen.addSeen(submission.getFullName());
                }
                LastComments.setComments(submission);
            }
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder firstHolder, int old) {
        int pos = old != 0 ? old - 1 : old;
        if (firstHolder instanceof CommentViewHolder) {
            final CommentViewHolder holder = (CommentViewHolder) firstHolder;
            int datasetPosition = pos - 1;

            datasetPosition = getRealPosition(datasetPosition);

            if (pos > toShiftTo) {
                shifted = 0;
            }
            if (pos < shiftFrom) {
                shifted = 0;
            }

            final CommentNode baseNode = currentComments.get(datasetPosition).comment;
            final Comment comment = baseNode.getComment();

            if (pos == getItemCount() - 1) {
                holder.itemView.setPadding(
                        0,
                        0,
                        0,
                        (int)
                                mContext.getResources()
                                        .getDimension(R.dimen.overview_top_padding_single));
            } else {
                holder.itemView.setPadding(0, 0, 0, 0);
            }

            doScoreText(holder, comment, this);

            // Long click listeners
            View.OnLongClickListener onLongClickListener =
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            if (SettingValues.swap) {
                                doOnClick(holder, comment, baseNode);
                            } else {
                                doLongClick(holder, comment, baseNode);
                            }
                            return true;
                        }
                    };

            holder.firstTextView.setOnLongClickListener(onLongClickListener);
            holder.commentOverflow.setOnLongClickListener(onLongClickListener);

            holder.itemView.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            if (!currentlyEditingId.equals(comment.getFullName())) {
                                if (SettingValues.swap) {
                                    doOnClick(holder, comment, baseNode);
                                } else {
                                    doLongClick(holder, comment, baseNode);
                                }
                            }
                            return true;
                        }
                    });

            // Single click listeners
            OnSingleClickListener singleClick =
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            if (!currentlyEditingId.equals(comment.getFullName())) {
                                if (SettingValues.swap) {
                                    doLongClick(holder, comment, baseNode);
                                } else {
                                    doOnClick(holder, comment, baseNode);
                                }
                            }
                        }
                    };
            holder.itemView.setOnClickListener(singleClick);
            holder.commentOverflow.setOnClickListener(singleClick);
            if (!toCollapse.contains(comment.getFullName()) || !SettingValues.collapseComments) {
                setViews(
                        comment.getDataNode().get("body_html").asText(),
                        submission.getSubredditName(),
                        holder,
                        singleClick,
                        onLongClickListener);
            }

            holder.firstTextView.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            SpoilerRobotoTextView SpoilerRobotoTextView = (SpoilerRobotoTextView) v;
                            if (SettingValues.swap) {
                                if (!SpoilerRobotoTextView.isSpoilerClicked()) {
                                    doLongClick(holder, comment, baseNode);
                                } else if (SpoilerRobotoTextView.isSpoilerClicked()) {
                                    SpoilerRobotoTextView.resetSpoilerClicked();
                                }
                            } else if (!SpoilerRobotoTextView.isSpoilerClicked()) {
                                doOnClick(holder, comment, baseNode);
                            } else if (SpoilerRobotoTextView.isSpoilerClicked()) {
                                SpoilerRobotoTextView.resetSpoilerClicked();
                            }
                        }
                    });
            if (ImageFlairs.isSynced(comment.getSubredditName())
                    && comment.getAuthorFlair() != null
                    && comment.getAuthorFlair().getCssClass() != null
                    && !comment.getAuthorFlair().getCssClass().isEmpty()) {
                boolean set = false;
                for (String s : comment.getAuthorFlair().getCssClass().split(" ")) {
                    ImageFlairs.FlairImageLoader loader = ImageFlairs.getFlairImageLoader(mContext);
                    File file =
                            DiskCacheUtils.findInCache(
                                    comment.getSubredditName().toLowerCase(Locale.ENGLISH)
                                            + ":"
                                            + s.toLowerCase(Locale.ENGLISH),
                                    loader.getDiskCache());
                    if (file != null && file.exists()) {
                        set = true;
                        holder.imageFlair.setVisibility(View.VISIBLE);
                        String decodedImgUri = Uri.fromFile(file).toString();
                        loader.displayImage(decodedImgUri, holder.imageFlair);
                        break;
                    }
                }
                if (!set) {
                    holder.imageFlair.setImageDrawable(null);
                    holder.imageFlair.setVisibility(View.GONE);
                }
            } else {
                holder.imageFlair.setVisibility(View.GONE);
            }
            // Set typeface for body
            int type = new FontPreferences(mContext).getFontTypeComment().getTypeface();
            Typeface typeface;
            if (type >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(mContext, type);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.firstTextView.setTypeface(typeface);

            // Show padding on top
            if (baseNode.isTopLevel()) {
                holder.itemView.findViewById(R.id.next).setVisibility(View.VISIBLE);
            } else if (holder.itemView.findViewById(R.id.next).getVisibility() == View.VISIBLE) {
                holder.itemView.findViewById(R.id.next).setVisibility(View.GONE);
            }

            // Should be collapsed?
            if (hiddenPersons.contains(comment.getFullName())
                    || toCollapse.contains(comment.getFullName())) {
                int childnumber = getChildNumber(baseNode);
                if (hiddenPersons.contains(comment.getFullName()) && childnumber > 0) {
                    holder.childrenNumber.setVisibility(View.VISIBLE);
                    holder.childrenNumber.setText("+" + childnumber);
                } else {
                    holder.childrenNumber.setVisibility(View.GONE);
                }
                if (SettingValues.collapseComments && toCollapse.contains(comment.getFullName())) {
                    holder.firstTextView.setVisibility(View.GONE);
                    holder.commentOverflow.setVisibility(View.GONE);
                }
            } else {
                holder.childrenNumber.setVisibility(View.GONE);
                holder.commentOverflow.setVisibility(View.VISIBLE);
            }

            holder.dot.setVisibility(View.VISIBLE);

            int dwidth =
                    (int)
                            ((SettingValues.largeDepth ? 5 : 3)
                                    * Resources.getSystem().getDisplayMetrics().density);
            int width = 0;

            // Padding on the left, starting with the third comment
            for (int i = 2; i < baseNode.getDepth(); i++) {
                width += dwidth;
            }
            RecyclerView.LayoutParams params =
                    (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            params.setMargins(width, 0, 0, 0);
            holder.itemView.setLayoutParams(params);
            RelativeLayout.LayoutParams params2 =
                    (RelativeLayout.LayoutParams) holder.dot.getLayoutParams();
            params2.width = dwidth;
            holder.dot.setLayoutParams(params2);
            if (baseNode.getDepth() - 1 > 0) {
                int i22 = baseNode.getDepth() - 2;
                String commentOp = dataSet.commentOPs.get(comment.getId());
                if (SettingValues.highlightCommentOP
                        && commentOp != null
                        && comment != null
                        && commentOp.equals(comment.getAuthor())) {
                    holder.dot.setBackgroundColor(
                            ContextCompat.getColor(mContext, R.color.md_purple_500));

                } else {
                    if (i22 % 5 == 0) {
                        holder.dot.setBackgroundColor(
                                ContextCompat.getColor(
                                        mContext,
                                        !SettingValues.colorCommentDepth
                                                ? R.color.md_grey_700
                                                : R.color.md_blue_500));
                    } else if (i22 % 4 == 0) {
                        holder.dot.setBackgroundColor(
                                ContextCompat.getColor(
                                        mContext,
                                        !SettingValues.colorCommentDepth
                                                ? R.color.md_grey_600
                                                : R.color.md_green_500));
                    } else if (i22 % 3 == 0) {
                        holder.dot.setBackgroundColor(
                                ContextCompat.getColor(
                                        mContext,
                                        !SettingValues.colorCommentDepth
                                                ? R.color.md_grey_500
                                                : R.color.md_yellow_500));
                    } else if (i22 % 2 == 0) {
                        holder.dot.setBackgroundColor(
                                ContextCompat.getColor(
                                        mContext,
                                        !SettingValues.colorCommentDepth
                                                ? R.color.md_grey_400
                                                : R.color.md_orange_500));
                    } else {
                        holder.dot.setBackgroundColor(
                                ContextCompat.getColor(
                                        mContext,
                                        !SettingValues.colorCommentDepth
                                                ? R.color.md_grey_300
                                                : R.color.md_red_500));
                    }
                }
            } else {
                holder.dot.setVisibility(View.GONE);
            }

            if (currentSelectedItem != null
                    && comment.getFullName().contains(currentSelectedItem)
                    && !currentSelectedItem.isEmpty()
                    && !currentlyEditingId.equals(comment.getFullName())) {
                doHighlighted(holder, comment, baseNode, false);
            } else if (!currentlyEditingId.equals(comment.getFullName())) {
                setCommentStateUnhighlighted(holder, baseNode, false);
            }

            if (deleted.contains(comment.getFullName())) {
                holder.firstTextView.setText(R.string.comment_deleted);
                holder.content.setText(R.string.comment_deleted);
            }

            if (currentlyEditingId.equals(comment.getFullName())) {
                setCommentStateUnhighlighted(holder, baseNode, false);
                setCommentStateHighlighted(holder, comment, baseNode, true, false);
            }

            if (SettingValues.collapseDeletedComments) {
                if (comment.getBody().startsWith("[removed]")
                        || comment.getBody().startsWith("[deleted]")) {
                    holder.firstTextView.setVisibility(View.GONE);
                    holder.commentOverflow.setVisibility(View.GONE);
                }
            }

            if (SettingValues.collapseStickyComment) {
                if (comment.getDataNode().has("stickied")
                        && comment.getDataNode().get("stickied").asBoolean()) {
                    holder.firstTextView.setVisibility(View.GONE);
                    holder.commentOverflow.setVisibility(View.GONE);
                }
            }

        } else if (firstHolder instanceof SubmissionViewHolder && submission != null) {
            submissionViewHolder = (SubmissionViewHolder) firstHolder;
            new PopulateSubmissionViewHolder()
                    .populateSubmissionViewHolder(
                            (SubmissionViewHolder) firstHolder,
                            submission,
                            (Activity) mContext,
                            true,
                            true,
                            null,
                            listView,
                            false,
                            false,
                            null,
                            this);
            if (Authentication.isLoggedIn && Authentication.didOnline) {
                if (submission.isArchived() || submission.isLocked()) {
                    firstHolder.itemView.findViewById(R.id.reply).setVisibility(View.GONE);
                } else {
                    firstHolder
                            .itemView
                            .findViewById(R.id.reply)
                            .setOnClickListener(
                                    new OnSingleClickListener() {
                                        @Override
                                        public void onSingleClick(View v) {
                                            doReplySubmission(firstHolder);
                                        }
                                    });
                    firstHolder
                            .itemView
                            .findViewById(R.id.discard)
                            .setOnClickListener(
                                    new OnSingleClickListener() {
                                        @Override
                                        public void onSingleClick(View v) {
                                            firstHolder
                                                    .itemView
                                                    .findViewById(R.id.innerSend)
                                                    .setVisibility(View.GONE);
                                            currentlyEditing = null;
                                            editingPosition = -1;
                                            if (SettingValues.fastscroll) {
                                                mPage.fastScroll.setVisibility(View.VISIBLE);
                                            }
                                            if (mPage.fab != null)
                                                mPage.fab.setVisibility(View.VISIBLE);
                                            mPage.overrideFab = false;
                                            currentlyEditingId = "";
                                            backedText = "";
                                            View view =
                                                    ((Activity) mContext)
                                                            .findViewById(android.R.id.content);
                                            if (view != null) {
                                                KeyboardUtil.hideKeyboard(
                                                        mContext, view.getWindowToken(), 0);
                                            }
                                        }
                                    });
                }
            } else {
                firstHolder.itemView.findViewById(R.id.innerSend).setVisibility(View.GONE);
                firstHolder.itemView.findViewById(R.id.reply).setVisibility(View.GONE);
            }

            firstHolder
                    .itemView
                    .findViewById(R.id.more)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View v) {
                                    firstHolder.itemView.findViewById(R.id.menu).callOnClick();
                                }
                            });

        } else if (firstHolder instanceof MoreCommentViewHolder) {
            final MoreCommentViewHolder holder = (MoreCommentViewHolder) firstHolder;
            int nextPos = pos - 1;

            nextPos = getRealPosition(nextPos);

            final MoreChildItem baseNode = (MoreChildItem) currentComments.get(nextPos);
            if (baseNode.children.getCount() > 0) {
                try {
                    holder.content.setText(
                            mContext.getString(
                                    R.string.comment_load_more_string_new,
                                    baseNode.children.getLocalizedCount()));
                } catch (Exception e) {
                    holder.content.setText(R.string.comment_load_more_number_unknown);
                }
            } else if (!baseNode.children.getChildrenIds().isEmpty()) {
                holder.content.setText(R.string.comment_load_more_number_unknown);
            } else {
                holder.content.setText(R.string.thread_continue);
            }

            int dwidth =
                    (int)
                            ((SettingValues.largeDepth ? 5 : 3)
                                    * Resources.getSystem().getDisplayMetrics().density);
            int width = 0;
            for (int i = 1; i < baseNode.comment.getDepth(); i++) {
                width += dwidth;
            }

            final View progress = holder.loading;
            progress.setVisibility(View.GONE);
            final int finalNextPos = nextPos;
            holder.content.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            if (baseNode.children.getChildrenIds().isEmpty()) {
                                String toGoTo =
                                        "https://reddit.com"
                                                + submission.getPermalink()
                                                + baseNode.comment.getComment().getId()
                                                + "?context=0";
                                OpenRedditLink.openUrl(mContext, toGoTo, true);
                            } else if (progress.getVisibility() == View.GONE) {
                                progress.setVisibility(View.VISIBLE);
                                holder.content.setText(R.string.comment_loading_more);
                                currentLoading =
                                        new AsyncLoadMoreTask(
                                                finalNextPos,
                                                holder.getBindingAdapterPosition(),
                                                holder,
                                                baseNode.comment.getComment().getFullName(),
                                                mContext,
                                                CommentAdapter.this,
                                                listView,
                                                currentComments,
                                                keys);
                                currentLoading.execute(baseNode);
                            }
                        }
                    });

            RecyclerView.LayoutParams params =
                    (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            params.setMargins(width, 0, 0, 0);
            holder.itemView.setLayoutParams(params);
        }
        if (firstHolder instanceof SpacerViewHolder) {
            // Make a space the size of the toolbar minus 1 so there isn't a gap
            firstHolder
                    .itemView
                    .findViewById(R.id.height)
                    .setLayoutParams(
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    (Constants.SINGLE_HEADER_VIEW_OFFSET
                                            - DisplayUtil.dpToPxVertical(1)
                                            + mPage.shownHeaders)));
        }
    }

    public AsyncLoadMoreTask currentLoading;
    public String changedProfile;

    private void doReplySubmission(RecyclerView.ViewHolder submissionViewHolder) {
        final View replyArea = submissionViewHolder.itemView.findViewById(R.id.innerSend);
        if (replyArea.getVisibility() == View.GONE) {
            expandSubmissionReply(replyArea);
            EditText replyLine = submissionViewHolder.itemView.findViewById(R.id.replyLine);
            DoEditorActions.doActions(
                    replyLine,
                    submissionViewHolder.itemView,
                    fm,
                    (Activity) mContext,
                    submission.isSelfPost() ? submission.getSelftext() : null,
                    new String[] {submission.getAuthor()});

            currentlyEditing = submissionViewHolder.itemView.findViewById(R.id.replyLine);

            final TextView profile = submissionViewHolder.itemView.findViewById(R.id.profile);
            changedProfile = Authentication.name;
            profile.setText("/u/" + changedProfile);
            profile.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final HashMap<String, String> accounts = new HashMap<>();

                            for (String s :
                                    Authentication.authentication.getStringSet(
                                            "accounts", new HashSet<String>())) {
                                if (s.contains(":")) {
                                    accounts.put(s.split(":")[0], s.split(":")[1]);
                                } else {
                                    accounts.put(s, "");
                                }
                            }
                            final ArrayList<String> keys = new ArrayList<>(accounts.keySet());
                            final int i = keys.indexOf(changedProfile);

                            new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.replies_switch_accounts)
                                    .setSingleChoiceItems(
                                            keys.toArray(new String[0]),
                                            i,
                                            (dialog, which) -> {
                                                changedProfile = keys.get(which);
                                                profile.setText("/u/" + changedProfile);
                                            })
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        }
                    });
            currentlyEditing.setOnFocusChangeListener(
                    new View.OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            if (hasFocus) {
                                mPage.fastScroll.setVisibility(View.GONE);
                                if (mPage.fab != null) mPage.fab.setVisibility(View.GONE);
                                mPage.overrideFab = true;
                            } else if (SettingValues.fastscroll) {
                                mPage.fastScroll.setVisibility(View.VISIBLE);
                                if (mPage.fab != null) mPage.fab.setVisibility(View.VISIBLE);
                                mPage.overrideFab = false;
                            }
                        }
                    });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                replyLine.setOnFocusChangeListener(
                        (v, b) -> {
                            if (b) {
                                v.postDelayed(
                                        () -> {
                                            if (!v.hasFocus()) v.requestFocus();
                                        },
                                        100);
                            }
                        });
            }
            replyLine.requestFocus();
            KeyboardUtil.toggleKeyboard(
                    mContext,
                    InputMethodManager.SHOW_FORCED,
                    InputMethodManager.HIDE_IMPLICIT_ONLY);

            editingPosition = submissionViewHolder.getBindingAdapterPosition();

            submissionViewHolder
                    .itemView
                    .findViewById(R.id.send)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View v) {
                                    dataSet.refreshLayout.setRefreshing(true);

                                    if (SettingValues.fastscroll) {
                                        mPage.fastScroll.setVisibility(View.VISIBLE);
                                    }
                                    if (mPage.fab != null) mPage.fab.setVisibility(View.VISIBLE);
                                    mPage.overrideFab = false;
                                    if (currentlyEditing != null) {
                                        String text = currentlyEditing.getText().toString();
                                        new ReplyTaskComment(submission, changedProfile)
                                                .execute(text);
                                        replyArea.setVisibility(View.GONE);
                                        currentlyEditing.setText("");
                                        currentlyEditing = null;
                                        editingPosition = -1;
                                        // Hide soft keyboard
                                        View view =
                                                ((Activity) mContext)
                                                        .findViewById(android.R.id.content);
                                        if (view != null) {
                                            KeyboardUtil.hideKeyboard(
                                                    mContext, view.getWindowToken(), 0);
                                        }
                                    }
                                }
                            });
        } else {
            View view = ((Activity) mContext).findViewById(android.R.id.content);
            if (view != null) {
                KeyboardUtil.hideKeyboard(mContext, view.getWindowToken(), 0);
            }
            collapseAndHide(replyArea);
        }
    }

    public void setViews(
            String rawHTML,
            String subredditName,
            final SpoilerRobotoTextView firstTextView,
            CommentOverflow commentOverflow) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0), subredditName);
            startIndex = 1;
        } else {
            firstTextView.setText("");
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                commentOverflow.setViews(blocks, subredditName);
            } else {
                commentOverflow.setViews(blocks.subList(startIndex, blocks.size()), subredditName);
            }
        } else {
            commentOverflow.removeAllViews();
        }
    }

    public void setViews(
            String rawHTML,
            String subredditName,
            final SpoilerRobotoTextView firstTextView,
            CommentOverflow commentOverflow,
            View.OnClickListener click,
            View.OnLongClickListener onLongClickListener) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0) + " ", subredditName);
            startIndex = 1;
        } else {
            firstTextView.setText("");
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                commentOverflow.setViews(blocks, subredditName, click, onLongClickListener);
            } else {
                commentOverflow.setViews(
                        blocks.subList(startIndex, blocks.size()),
                        subredditName,
                        click,
                        onLongClickListener);
            }
        } else {
            commentOverflow.removeAllViews();
        }
    }

    public void setViews(String rawHTML, String subredditName, CommentViewHolder holder) {
        setViews(rawHTML, subredditName, holder.firstTextView, holder.commentOverflow);
    }

    private void setViews(
            String rawHTML,
            String subredditName,
            CommentViewHolder holder,
            View.OnClickListener click,
            View.OnLongClickListener longClickListener) {
        setViews(
                rawHTML,
                subredditName,
                holder.firstTextView,
                holder.commentOverflow,
                click,
                longClickListener);
    }

    public int editingPosition;

    private void collapseAndHide(final View v) {
        int finalHeight = v.getHeight();

        mAnimator = AnimatorUtil.slideAnimator(finalHeight, 0, v);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        // Height=0, but it set visibility to GONE
                        v.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        v.setVisibility(View.GONE);
                    }
                });
        mAnimator.start();
    }

    private void collapseAndRemove(final View v) {
        int finalHeight = v.getHeight();

        mAnimator = AnimatorUtil.slideAnimator(finalHeight, 0, v);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        // Height=0, but it set visibility to GONE
                        ((LinearLayout) v).removeAllViews();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        ((LinearLayout) v).removeAllViews();
                    }
                });
        mAnimator.start();
    }

    public void doShowMenu(final View l) {
        l.setVisibility(View.VISIBLE);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l.measure(widthSpec, heightSpec);

        final View l2 = l.findViewById(R.id.menu);
        final int widthSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l2.measure(widthSpec2, heightSpec2);
        ValueAnimator mAnimator =
                AnimatorUtil.slideAnimator(l.getMeasuredHeight(), l2.getMeasuredHeight(), l);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        l2.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        l2.setVisibility(View.VISIBLE);
                    }
                });
        mAnimator.start();
    }

    ValueAnimator mAnimator;

    public void expand(final View l) { // Made public (was private)
        l.setVisibility(View.VISIBLE);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l.measure(widthSpec, heightSpec);

        View l2 =
                l.findViewById(R.id.replyArea) == null
                        ? l.findViewById(R.id.innerSend)
                        : l.findViewById(R.id.replyArea);
        final int widthSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l2.measure(widthSpec2, heightSpec2);

        mAnimator =
                AnimatorUtil.slideAnimator(0, l.getMeasuredHeight() - l2.getMeasuredHeight(), l);

        mAnimator.start();
    }

    public void expandAndSetParams(final View l) { // Made public (was private)
        l.setVisibility(View.VISIBLE);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l.measure(widthSpec, heightSpec);

        View l2 =
                l.findViewById(R.id.replyArea) == null
                        ? l.findViewById(R.id.innerSend)
                        : l.findViewById(R.id.replyArea);
        final int widthSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l2.measure(widthSpec2, heightSpec2);

        mAnimator =
                AnimatorUtil.slideAnimator(
                        (l.getMeasuredHeight() - l2.getMeasuredHeight()),
                        l.getMeasuredHeight() - (l.getMeasuredHeight() - l2.getMeasuredHeight()),
                        l);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        RelativeLayout.LayoutParams params =
                                (RelativeLayout.LayoutParams) l.getLayoutParams();
                        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        params.addRule(RelativeLayout.BELOW, R.id.commentOverflow);
                        l.setLayoutParams(params);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        RelativeLayout.LayoutParams params =
                                (RelativeLayout.LayoutParams) l.getLayoutParams();
                        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        params.addRule(RelativeLayout.BELOW, R.id.commentOverflow);
                        l.setLayoutParams(params);
                    }
                });
        mAnimator.start();
    }

    private void expandSubmissionReply(final View l) {
        l.setVisibility(View.VISIBLE);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        l.measure(widthSpec, heightSpec);

        mAnimator = AnimatorUtil.slideAnimator(0, l.getMeasuredHeight(), l);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        LinearLayout.LayoutParams params =
                                (LinearLayout.LayoutParams) l.getLayoutParams();
                        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        l.setLayoutParams(params);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        LinearLayout.LayoutParams params =
                                (LinearLayout.LayoutParams) l.getLayoutParams();
                        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        l.setLayoutParams(params);
                    }
                });
        mAnimator.start();
    }

    public CommentNode currentBaseNode;

    public void setCommentStateHighlighted(
            final CommentViewHolder holder,
            final Comment n,
            final CommentNode baseNode,
            boolean isReplying,
            boolean animate) {
        // Call the extracted static method
        CommentStateUtil.handleSetCommentStateHighlighted(this, holder, n, baseNode, isReplying, animate);
    }

    public void doHighlighted(
            final CommentViewHolder holder,
            final Comment n,
            final CommentNode baseNode,
            boolean animate) {
        if (mAnimator != null && mAnimator.isRunning()) {
            holder.itemView.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            setCommentStateHighlighted(holder, n, baseNode, false, true);
                        }
                    },
                    mAnimator.getDuration());
        } else {
            setCommentStateHighlighted(holder, n, baseNode, false, animate);
        }
    }

    public EditText currentlyEditing;

    public void resetMenu(LinearLayout v, boolean collapsed) {
        v.removeAllViews();
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.getLayoutParams();
        if (collapsed) {
            params.height = 0;
        } else {
            params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        }
        v.setLayoutParams(params);
    }

    public void setCommentStateUnhighlighted(
            final CommentViewHolder holder, final CommentNode baseNode, boolean animate) {
        if (animate) {
            collapseAndRemove(holder.menuArea);
        } else {
            resetMenu(holder.menuArea, true);
        }

        int color;

        Comment c = baseNode.getComment();
        if (lastSeen != 0
                && lastSeen < c.getCreated().getTime()
                && !dataSet.single
                && SettingValues.commentLastVisit
                && !Authentication.name.equals(c.getAuthor())) {
            color = Palette.getColor(baseNode.getComment().getSubredditName());
            color = Color.argb(20, Color.red(color), Color.green(color), Color.blue(color));
        } else {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = mContext.getTheme();
            theme.resolveAttribute(R.attr.card_background, typedValue, true);
            color = typedValue.data;
        }
        int dwidth = (int) (3 * Resources.getSystem().getDisplayMetrics().density);
        int width = 0;

        // Padding on the left, starting with the third comment
        for (int i = 2; i < baseNode.getDepth(); i++) {
            width += dwidth;
        }
        RecyclerView.LayoutParams params =
                (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        params.setMargins(width, 0, 0, 0);
        holder.itemView.setLayoutParams(params);

        holder.itemView.findViewById(R.id.background).setBackgroundColor(color);
    }

    public void setCommentStateUnhighlighted(
            final CommentViewHolder holder,
            final Comment comment,
            final CommentNode baseNode,
            boolean animate) {
        if (currentlyEditing != null
                && !currentlyEditing.getText().toString().isEmpty()
                && holder.getBindingAdapterPosition() <= editingPosition) {
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.discard_comment_title)
                    .setMessage(R.string.comment_discard_msg)
                    .setPositiveButton(
                            R.string.btn_yes,
                            (dialog, which) -> {
                                currentlyEditing = null;
                                editingPosition = -1;
                                if (SettingValues.fastscroll) {
                                    mPage.fastScroll.setVisibility(View.VISIBLE);
                                }
                                if (mPage.fab != null) mPage.fab.setVisibility(View.VISIBLE);
                                mPage.overrideFab = false;
                                currentlyEditingId = "";
                                backedText = "";
                                View view =
                                        ((Activity) mContext).findViewById(android.R.id.content);
                                if (view != null) {
                                    KeyboardUtil.hideKeyboard(mContext, view.getWindowToken(), 0);
                                }

                                setCommentStateUnhighlighted(holder, comment, baseNode, true);
                            })
                    .setNegativeButton(R.string.btn_no, null)
                    .show();
        } else {
            currentlySelected = null;
            currentSelectedItem = "";
            if (animate) {
                collapseAndRemove(holder.menuArea);
            } else {
                resetMenu(holder.menuArea, true);
            }
            int dwidth = (int) (3 * Resources.getSystem().getDisplayMetrics().density);
            int width = 0;

            // Padding on the left, starting with the third comment
            for (int i = 2; i < baseNode.getDepth(); i++) {
                width += dwidth;
            }
            RecyclerView.LayoutParams params =
                    (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            params.setMargins(width, 0, 0, 0);
            holder.itemView.setLayoutParams(params);

            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = mContext.getTheme();
            theme.resolveAttribute(R.attr.card_background, typedValue, true);
            int color = typedValue.data;
            holder.itemView.findViewById(R.id.background).setBackgroundColor(color);
        }
    }

    public void doLongClick(
            final CommentViewHolder holder, final Comment comment, final CommentNode baseNode) {
        if (currentlyEditing != null && !currentlyEditing.getText().toString().isEmpty()) {
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.discard_comment_title)
                    .setMessage(R.string.comment_discard_msg)
                    .setPositiveButton(
                            R.string.btn_yes,
                            (dialog, which) -> {
                                currentlyEditing = null;
                                editingPosition = -1;
                                if (SettingValues.fastscroll) {
                                    mPage.fastScroll.setVisibility(View.VISIBLE);
                                }
                                if (mPage.fab != null) mPage.fab.setVisibility(View.VISIBLE);
                                mPage.overrideFab = false;
                                currentlyEditingId = "";
                                backedText = "";
                                View view =
                                        ((Activity) mContext).findViewById(android.R.id.content);
                                if (view != null) {
                                    KeyboardUtil.hideKeyboard(mContext, view.getWindowToken(), 0);
                                }

                                doLongClick(holder, comment, baseNode);
                            })
                    .setNegativeButton(R.string.btn_no, null)
                    .show();

        } else {
            if (currentSelectedItem != null
                    && currentSelectedItem.contains(comment.getFullName())) {
                setCommentStateUnhighlighted(holder, comment, baseNode, true);
            } else {
                doHighlighted(holder, comment, baseNode, true);
            }
        }
    }

    public void doOnClick(CommentViewHolder holder, Comment comment, CommentNode baseNode) {
        if (currentSelectedItem != null && currentSelectedItem.contains(comment.getFullName())) {
            if (SettingValues.swap) {
                // If the comment is highlighted and the user is long pressing the comment,
                // hide the comment.
                doOnClick(holder, baseNode, comment);
            }
            setCommentStateUnhighlighted(holder, comment, baseNode, true);
        } else {
            doOnClick(holder, baseNode, comment);
        }
    }

    public void doOnClick(
            final CommentViewHolder holder, final CommentNode baseNode, final Comment comment) {
        if (currentlyEditing != null
                && !currentlyEditing.getText().toString().isEmpty()
                && holder.getBindingAdapterPosition() <= editingPosition) {
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.discard_comment_title)
                    .setMessage(R.string.comment_discard_msg)
                    .setPositiveButton(
                            R.string.btn_yes,
                            (dialog, which) -> {
                                currentlyEditing = null;
                                editingPosition = -1;
                                if (SettingValues.fastscroll) {
                                    mPage.fastScroll.setVisibility(View.VISIBLE);
                                }
                                if (mPage.fab != null) mPage.fab.setVisibility(View.VISIBLE);
                                mPage.overrideFab = false;
                                currentlyEditingId = "";
                                backedText = "";
                                View view =
                                        ((Activity) mContext).findViewById(android.R.id.content);
                                if (view != null) {
                                    KeyboardUtil.hideKeyboard(mContext, view.getWindowToken(), 0);
                                }

                                doOnClick(holder, baseNode, comment);
                            })
                    .setNegativeButton(R.string.btn_no, null)
                    .show();

        } else {
            if (isClicking) {
                isClicking = false;
                resetMenu(holder.menuArea, true);
                isHolder.itemView.findViewById(R.id.menu).setVisibility(View.GONE);
            } else {
                if (hiddenPersons.contains(comment.getFullName())) {
                    hiddenPersons.remove(comment.getFullName());
                    unhideAll(baseNode, holder.getBindingAdapterPosition() + 1);

                    if (toCollapse.contains(comment.getFullName())
                            && SettingValues.collapseComments) {
                        setViews(
                                comment.getDataNode().get("body_html").asText(),
                                submission.getSubredditName(),
                                holder);
                    }

                    CommentAdapterHelper.hideChildrenObject(holder.childrenNumber);
                    if (!holder.firstTextView.getText().toString().isEmpty()) {
                        holder.firstTextView.setVisibility(View.VISIBLE);
                    } else {
                        holder.firstTextView.setVisibility(View.GONE);
                    }
                    holder.commentOverflow.setVisibility(View.VISIBLE);

                    toCollapse.remove(comment.getFullName());

                } else {
                    int childNumber = getChildNumber(baseNode);
                    if (childNumber > 0) {
                        hideAll(baseNode, holder.getBindingAdapterPosition() + 1);
                        if (!hiddenPersons.contains(comment.getFullName())) {
                            hiddenPersons.add(comment.getFullName());
                        }
                        if (childNumber > 0) {
                            CommentAdapterHelper.showChildrenObject(holder.childrenNumber);
                            holder.childrenNumber.setText("+" + childNumber);
                        }
                    } else {
                        if (!SettingValues.collapseComments) {
                            doLongClick(holder, comment, baseNode);
                        }
                    }
                    toCollapse.add(comment.getFullName());
                    if ((holder.firstTextView.getVisibility() == View.VISIBLE
                                    || holder.commentOverflow.getVisibility() == View.VISIBLE)
                            && SettingValues.collapseComments) {
                        holder.firstTextView.setVisibility(View.GONE);
                        holder.commentOverflow.setVisibility(View.GONE);
                    } else if (SettingValues.collapseComments) {
                        if (!holder.firstTextView.getText().toString().isEmpty()) {
                            holder.firstTextView.setVisibility(View.VISIBLE);
                        } else {
                            holder.firstTextView.setVisibility(View.GONE);
                        }
                        holder.commentOverflow.setVisibility(View.VISIBLE);
                    }
                }
                clickpos = holder.getBindingAdapterPosition() + 1;
            }
        }
    }

    private int getChildNumber(CommentNode user) {
        int i = 0;
        for (CommentNode ignored : user.walkTree()) {
            i++;
            if (ignored.hasMoreComments() && dataSet.online) {
                i++;
            }
        }

        return i - 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0
                || (currentComments != null
                        && !currentComments.isEmpty()
                        && position == (currentComments.size() - hidden.size()) + 2)
                || (currentComments != null && currentComments.isEmpty() && position == 2)) {
            return SPACER;
        } else {
            position -= 1;
        }
        if (position == 0) {
            return HEADER;
        }

        return (currentComments.get(getRealPosition(position - 1)) instanceof CommentItem ? 2 : 3);
    }

    @Override
    public int getItemCount() {
        if (currentComments == null) {
            return 2;
        } else {
            return 3 + (currentComments.size() - hidden.size());
        }
    }

    public void unhideAll(CommentNode n, int i) {
        try {
            int counter = unhideNumber(n, 0);
            if (SettingValues.collapseComments) {
                listView.setItemAnimator(null);
            } else {
                try {
                    listView.setItemAnimator(new AlphaInAnimator());
                } catch (Exception ignored) {
                }
            }
            notifyItemRangeInserted(i, counter);
        } catch (Exception ignored) {

        }
    }

    public void unhideAll(CommentNode n) {
        unhideNumber(n, 0);
        if (SettingValues.collapseComments) {
            listView.setItemAnimator(null);
        } else {
            listView.setItemAnimator(new AlphaInAnimator());
        }
        notifyDataSetChanged();
    }

    public void hideAll(CommentNode n) {

        hideNumber(n, 0);
        if (SettingValues.collapseComments) {
            listView.setItemAnimator(null);
        } else {
            listView.setItemAnimator(new AlphaInAnimator());
        }
        notifyDataSetChanged();
    }

    public void hideAll(CommentNode n, int i) {

        int counter = hideNumber(n, 0);
        if (SettingValues.collapseComments) {
            listView.setItemAnimator(null);
        } else {
            listView.setItemAnimator(new AlphaInAnimator());
        }
        notifyItemRangeRemoved(i, counter);
    }

    public boolean parentHidden(CommentNode n) {
        n = n.getParent();
        while (n != null && n.getDepth() > 0) {
            String name = n.getComment().getFullName();
            if (hiddenPersons.contains(name) || hidden.contains(name)) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    public int unhideNumber(CommentNode n, int i) {
        for (CommentNode ignored : n.getChildren()) {

            if (!ignored.getComment().getFullName().equals(n.getComment().getFullName())) {
                boolean parentHidden = parentHidden(ignored);

                if (parentHidden) {
                    continue;
                }

                String name = ignored.getComment().getFullName();

                if (hidden.contains(name) || hiddenPersons.contains(name)) {
                    hidden.remove(name);
                    i++;

                    if (ignored.hasMoreComments()
                            && !hiddenPersons.contains(name)
                            && dataSet.online) {
                        name = name + "more";
                        if (hidden.contains(name)) {
                            hidden.remove(name);
                            toCollapse.remove(name);
                            i++;
                        }
                    }
                }
                i += unhideNumber(ignored, 0);
            }
        }
        if (n.hasMoreComments()
                && !parentHidden(n)
                && !hiddenPersons.contains(n.getComment().getFullName())
                && dataSet.online) {
            String fullname = n.getComment().getFullName() + "more";

            if (hidden.contains(fullname)) {
                i++;
                hidden.remove(fullname);
            }
        }
        return i;
    }

    public int hideNumber(CommentNode n, int i) {
        for (CommentNode ignored : n.getChildren()) {
            if (!ignored.getComment().getFullName().equals(n.getComment().getFullName())) {
                String fullname = ignored.getComment().getFullName();

                if (!hidden.contains(fullname)) {
                    i++;
                    hidden.add(fullname);
                }
                if (ignored.hasMoreComments() && dataSet.online) {
                    if (currentLoading != null && currentLoading.fullname.equals(fullname)) {
                        currentLoading.cancel(true);
                    }

                    fullname = fullname + "more";

                    if (!hidden.contains(fullname)) {
                        i++;
                        hidden.add(fullname);
                    }
                }
                i += hideNumber(ignored, 0);
            }
        }
        if (n.hasMoreComments() && dataSet.online) {
            String fullname = n.getComment().getFullName() + "more";
            if (!hidden.contains(fullname)) {
                i++;
                hidden.add(fullname);
            }
        }
        return i;
    }

    public String[] getParents(CommentNode comment) {
        String[] bodies = new String[comment.getDepth() + 1];
        bodies[0] = comment.getComment().getAuthor();

        CommentNode parent = comment.getParent();
        int index = 1;

        while (parent != null) {
            bodies[index] = parent.getComment().getAuthor();
            index++;
            parent = parent.getParent();
        }

        bodies[index - 1] = submission.getAuthor();

        // Reverse the array so Submission > Author > ... > Current OP
        for (int i = 0; i < bodies.length / 2; i++) {
            String temp = bodies[i];
            bodies[i] = bodies[bodies.length - i - 1];
            bodies[bodies.length - i - 1] = temp;
        }

        return bodies;
    }

    public int getRealPosition(int position) {
        int hElements = getHiddenCountUpTo(position);
        int diff = 0;
        for (int i = 0; i < hElements; i++) {
            diff++;
            if ((currentComments.size() > position + diff)
                    && hidden.contains(currentComments.get(position + diff).getName())) {
                i--;
            }
        }
        return (position + diff);
    }

    private int getHiddenCountUpTo(int location) {
        int count = 0;
        for (int i = 0; (i <= location && i < currentComments.size()); i++) {
            if (currentComments.size() > i && hidden.contains(currentComments.get(i).getName())) {
                count++;
            }
        }
        return count;
    }

    public class AsyncForceLoadChild extends AsyncTask<String, Void, Integer> {
        CommentNode node;
        public int holderPos;
        public int position;

        public AsyncForceLoadChild(int position, int holderPos, CommentNode baseNode) {
            this.holderPos = holderPos;
            this.node = baseNode;
            this.position = position;
        }

        @Override
        public void onPostExecute(Integer data) {
            if (data != -1) {
                listView.setItemAnimator(new SlideRightAlphaAnimator());

                notifyItemInserted(holderPos + 1);

                currentPos = holderPos + 1;
                toShiftTo =
                        ((LinearLayoutManager) listView.getLayoutManager())
                                .findLastVisibleItemPosition();
                shiftFrom =
                        ((LinearLayoutManager) listView.getLayoutManager())
                                .findFirstVisibleItemPosition();

                dataSet.refreshLayout.setRefreshing(false);
            } else {
                // Comment could not be found, force a reload
                Handler handler2 = new Handler();
                handler2.postDelayed(
                        new Runnable() {
                            public void run() {
                                ((Activity) mContext)
                                        .runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dataSet.refreshLayout.setRefreshing(false);
                                                        dataSet.loadMoreReply(CommentAdapter.this);
                                                    }
                                                });
                            }
                        },
                        2000);
            }
        }

        @Override
        protected Integer doInBackground(String... params) {

            int i = 0;

            if (params.length > 0) {
                try {
                    node.insertComment(Authentication.reddit, "t1_" + params[0]);
                    for (CommentNode n : node.walkTree()) {
                        if (n.getComment().getFullName().contains(params[0])) {
                            currentComments.add(position, new CommentItem(n));
                            i++;
                        }
                    }

                } catch (Exception e) {
                    Log.w(LogUtil.getTag(), "Cannot load more comments " + e);
                    i = -1;
                }

                shifted += i;

                if (currentComments != null) {
                    for (int i2 = 0; i2 < currentComments.size(); i2++) {
                        keys.put(currentComments.get(i2).getName(), i2);
                    }
                } else {
                    i = -1;
                }
            }
            return i;
        }
    }

    public void editComment(CommentNode n, CommentViewHolder holder) {
        if (n == null) {
            dataSet.loadMoreReply(this);
        } else {
            int position = getRealPosition(holder.getBindingAdapterPosition() - 1);
            final int holderpos = holder.getBindingAdapterPosition();
            currentComments.remove(position - 1);
            currentComments.add(position - 1, new CommentItem(n));
            listView.setItemAnimator(new SlideRightAlphaAnimator());
            ((Activity) mContext)
                    .runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    notifyItemChanged(holderpos);
                                }
                            });
        }
    }

    public class ReplyTaskComment extends AsyncTask<String, Void, String> {
        public Contribution sub;
        CommentNode node;
        CommentViewHolder holder;
        boolean isSubmission;
        String profileName;

        public ReplyTaskComment(
                Contribution n, CommentNode node, CommentViewHolder holder, String profileName) {
            sub = n;
            this.holder = holder;
            this.node = node;
            this.profileName = profileName;
        }

        public ReplyTaskComment(Contribution n, String profileName) {
            sub = n;
            isSubmission = true;
            this.profileName = profileName;
        }

        @Override
        public void onPostExecute(final String s) {
            if (s == null || s.isEmpty()) {

                if (commentBack != null && !commentBack.isEmpty()) {
                    Drafts.addDraft(commentBack);
                    try {
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.err_comment_post)
                                .setMessage(
                                        ((why == null)
                                                        ? ""
                                                        : mContext.getString(
                                                                R.string.err_comment_post_reason,
                                                                why))
                                                + mContext.getString(
                                                        R.string.err_comment_post_message))
                                .setPositiveButton(R.string.btn_ok, null)
                                .show();
                    } catch (Exception ignored) {

                    }
                } else {
                    try {
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.err_comment_post)
                                .setMessage(
                                        ((why == null)
                                                        ? ""
                                                        : mContext.getString(
                                                                R.string.err_comment_post_reason,
                                                                why))
                                                + mContext.getString(
                                                        R.string.err_comment_post_nosave_message))
                                .setPositiveButton(R.string.btn_ok, null)
                                .show();
                    } catch (Exception ignored) {

                    }
                }
            } else {
                if (isSubmission) {
                    new AsyncForceLoadChild(0, 0, submission.getComments()).execute(s);
                } else {
                    new AsyncForceLoadChild(
                                    getRealPosition(holder.getBindingAdapterPosition() - 1),
                                    holder.getBindingAdapterPosition(),
                                    node)
                            .execute(s);
                }
            }
        }

        String why;
        String commentBack;

        @Override
        protected String doInBackground(String... comment) {
            if (Authentication.me != null) {
                try {
                    commentBack = comment[0];
                    if (profileName.equals(Authentication.name)) {
                        return new AccountManager(Authentication.reddit).reply(sub, comment[0]);
                    } else {
                        LogUtil.v("Switching to " + profileName);
                        return new AccountManager(getAuthenticatedClient(profileName))
                                .reply(sub, comment[0]);
                    }
                } catch (Exception e) {
                    if (e instanceof ApiException) {
                        why = ((ApiException) e).getExplanation();
                    }
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private RedditClient getAuthenticatedClient(String profileName) {
        String token;
        RedditClient reddit =
                new RedditClient(
                        UserAgent.of("android:me.edgan.RedditSlide:v" + BuildConfig.VERSION_NAME));
        final HashMap<String, String> accounts = new HashMap<>();

        for (String s :
                Authentication.authentication.getStringSet("accounts", new HashSet<String>())) {
            if (s.contains(":")) {
                accounts.put(s.split(":")[0], s.split(":")[1]);
            } else {
                accounts.put(s, "");
            }
        }
        final ArrayList<String> keys = new ArrayList<>(accounts.keySet());
        if (accounts.containsKey(profileName) && !accounts.get(profileName).isEmpty()) {
            token = accounts.get(profileName);
        } else {
            ArrayList<String> tokens =
                    new ArrayList<>(
                            Authentication.authentication.getStringSet(
                                    "tokens", new HashSet<String>()));
            int index = keys.indexOf(profileName);
            if (keys.indexOf(profileName) > tokens.size()) {
                index -= 1;
            }
            token = tokens.get(index);
        }
        Authentication.doVerify(token, reddit, true, mContext);
        return reddit;
    }
}
