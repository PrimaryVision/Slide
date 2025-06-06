package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;

import net.dean.jraw.RedditClient;
import net.dean.jraw.http.LoggingMode;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.OkHttpAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.LoggedInAccount;

import okhttp3.Protocol;

import java.util.Calendar;
import java.util.HashSet;
import java.util.UUID;

/** Created by ccrama on 3/30/2015. */
public class Authentication {
    public static boolean isLoggedIn;
    public static RedditClient reddit;
    public static LoggedInAccount me;
    public static boolean mod;
    public static String name;
    public static SharedPreferences authentication;
    public static String refresh;

    public boolean hasDone;
    public static boolean didOnline;
    private static OkHttpAdapter httpAdapter;

    public static void resetAdapter() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (httpAdapter != null && httpAdapter.getNativeClient() != null) {
                    httpAdapter.getNativeClient().connectionPool().evictAll();
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public Authentication(Context context) {
        Reddit.setDefaultErrorHandler(context);

        if (NetworkUtil.isConnected(context)) {
            hasDone = true;
            httpAdapter = new OkHttpAdapter(Reddit.client, Protocol.HTTP_2);
            isLoggedIn = false;
            reddit =
                    new RedditClient(
                            UserAgent.of(
                                    "android:me.edgan.RedditSlide:v" + BuildConfig.VERSION_NAME),
                            httpAdapter);
            reddit.setRetryLimit(2);
            if (BuildConfig.DEBUG) reddit.setLoggingMode(LoggingMode.ALWAYS);
            didOnline = true;
            new VerifyCredentials(context).execute();
        } else {
            isLoggedIn = Reddit.appRestart.getBoolean("loggedin", false);
            name = Reddit.appRestart.getString("name", "");
            if ((name.isEmpty() || !isLoggedIn)
                    && !authentication.getString("lasttoken", "").isEmpty()) {
                for (String s :
                        Authentication.authentication.getStringSet(
                                "accounts", new HashSet<String>())) {
                    if (s.contains(authentication.getString("lasttoken", ""))) {
                        name = (s.split(":")[0]);
                        break;
                    }
                }
                isLoggedIn = true;
            }
        }
    }

    public void updateToken(Context c) {
        if (BuildConfig.DEBUG) LogUtil.v("Executing update token");
        if (reddit == null) {
            hasDone = true;
            isLoggedIn = false;
            reddit =
                    new RedditClient(
                            UserAgent.of(
                                    "android:me.edgan.RedditSlide:v" + BuildConfig.VERSION_NAME));
            reddit.setLoggingMode(LoggingMode.ALWAYS);
            didOnline = true;

            new VerifyCredentials(c).execute();
        } else {
            new UpdateToken(c).execute();
        }
    }

    public static boolean authedOnce;

    public static class UpdateToken extends AsyncTask<Void, Void, Void> {

        Context context;

        public UpdateToken(Context c) {
            this.context = c;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (authedOnce && NetworkUtil.isConnected(context)) {
                didOnline = true;
                if (name != null && !name.isEmpty()) {
                    Log.v(LogUtil.getTag(), "REAUTH");
                    if (isLoggedIn) {
                        try {

                            final Credentials credentials =
                                    Credentials.installedApp(
                                            Constants.getClientId(), Constants.REDDIT_REDIRECT_URL);
                            Log.v(LogUtil.getTag(), "REAUTH LOGGED IN");

                            OAuthHelper oAuthHelper = reddit.getOAuthHelper();

                            oAuthHelper.setRefreshToken(refresh);
                            OAuthData finalData;
                            if (authentication.contains("backedCreds")
                                    && authentication.getLong("expires", 0)
                                            > Calendar.getInstance().getTimeInMillis()) {
                                finalData =
                                        oAuthHelper.refreshToken(
                                                credentials,
                                                authentication.getString(
                                                        "backedCreds", "")); // does a request
                            } else {
                                finalData = oAuthHelper.refreshToken(credentials); // does a request
                                authentication
                                        .edit()
                                        .putLong(
                                                "expires",
                                                Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                        .commit();
                            }
                            authentication
                                    .edit()
                                    .putString("backedCreds", finalData.getDataNode().toString())
                                    .commit();
                            reddit.authenticate(finalData);
                            refresh = oAuthHelper.getRefreshToken();
                            refresh = reddit.getOAuthHelper().getRefreshToken();

                            if (reddit.isAuthenticated()) {
                                if (me == null) {
                                    me = reddit.me();
                                }
                                Authentication.isLoggedIn = true;
                            }
                            Log.v(LogUtil.getTag(), "AUTHENTICATED");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else {
                        final Credentials fcreds =
                                Credentials.userlessApp(Constants.getClientId(), UUID.randomUUID());
                        OAuthData authData;
                        if (BuildConfig.DEBUG) LogUtil.v("Not logged in");
                        try {

                            authData = reddit.getOAuthHelper().easyAuth(fcreds);
                            authentication
                                    .edit()
                                    .putLong(
                                            "expires",
                                            Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                    .commit();
                            authentication
                                    .edit()
                                    .putString("backedCreds", authData.getDataNode().toString())
                                    .commit();
                            Authentication.name = "LOGGEDOUT";
                            mod = false;

                            reddit.authenticate(authData);
                            Log.v(LogUtil.getTag(), "REAUTH LOGGED IN");

                        } catch (Exception e) {
                            try {
                                ((Activity) context)
                                        .runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        try {

                                                            new AlertDialog.Builder(context)
                                                                    .setTitle(R.string.err_general)
                                                                    .setMessage(
                                                                            R.string
                                                                                    .err_no_connection)
                                                                    .setPositiveButton(
                                                                            R.string.btn_yes,
                                                                            (dialog, which) ->
                                                                                    new UpdateToken(
                                                                                                    context)
                                                                                            .executeOnExecutor(
                                                                                                    AsyncTask
                                                                                                            .THREAD_POOL_EXECUTOR))
                                                                    .setNegativeButton(
                                                                            R.string.btn_no,
                                                                            (dialog, which) ->
                                                                                    Reddit
                                                                                            .forceRestart(
                                                                                                    context,
                                                                                                    false))
                                                                    .show();
                                                        } catch (Exception ignored) {

                                                        }
                                                    }
                                                });
                            } catch (Exception e2) {
                                Toast.makeText(
                                                context,
                                                "Reddit could not be reached. Try again soon",
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }

                            // TODO fail
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG) LogUtil.v("Done loading token");
            return null;
        }
    }

    public static class VerifyCredentials extends AsyncTask<String, Void, Void> {
        Context mContext;
        String lastToken;
        boolean single;

        public VerifyCredentials(Context context) {
            mContext = context;
            lastToken = authentication.getString("lasttoken", "");
        }

        @Override
        protected Void doInBackground(String... subs) {
            doVerify(lastToken, reddit, single, mContext);
            return null;
        }
    }

    public static void doVerify(
            String lastToken, RedditClient baseReddit, boolean single, Context mContext) {
        try {

            if (BuildConfig.DEBUG) LogUtil.v("TOKEN IS " + lastToken);
            if (!lastToken.isEmpty()) {

                final Credentials credentials =
                        Credentials.installedApp(
                                Constants.getClientId(), Constants.REDDIT_REDIRECT_URL);

                OAuthHelper oAuthHelper = baseReddit.getOAuthHelper();
                oAuthHelper.setRefreshToken(lastToken);

                try {
                    OAuthData finalData;
                    if (!single
                            && authentication.contains("backedCreds")
                            && authentication.getLong("expires", 0)
                                    > Calendar.getInstance().getTimeInMillis()) {
                        finalData =
                                oAuthHelper.refreshToken(
                                        credentials, authentication.getString("backedCreds", ""));
                    } else {
                        finalData = oAuthHelper.refreshToken(credentials); // does a request
                        if (!single) {
                            authentication
                                    .edit()
                                    .putLong(
                                            "expires",
                                            Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                    .apply();
                        }
                    }
                    baseReddit.authenticate(finalData);

                    if (!single) {
                        authentication
                                .edit()
                                .putString("backedCreds", finalData.getDataNode().toString())
                                .apply();
                        refresh = oAuthHelper.getRefreshToken();
                        if (BuildConfig.DEBUG) {
                            LogUtil.v("ACCESS TOKEN IS " + finalData.getAccessToken());
                        }

                        Authentication.isLoggedIn = true;

                        UserSubscriptions.doCachedModSubs();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if (e instanceof NetworkException) {
                        Toast.makeText(
                                        mContext,
                                        "Error "
                                                + ((NetworkException) e)
                                                        .getResponse()
                                                        .getStatusMessage()
                                                + ": "
                                                + (e).getMessage(),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                }
                didOnline = true;

            } else if (!single) {
                if (BuildConfig.DEBUG) LogUtil.v("NOT LOGGED IN");

                final Credentials fcreds =
                        Credentials.userlessApp(Constants.getClientId(), UUID.randomUUID());
                OAuthData authData;
                try {

                    authData = reddit.getOAuthHelper().easyAuth(fcreds);
                    authentication
                            .edit()
                            .putLong("expires", Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                            .apply();
                    authentication
                            .edit()
                            .putString("backedCreds", authData.getDataNode().toString())
                            .apply();
                    reddit.authenticate(authData);

                    Authentication.name = "LOGGEDOUT";
                    Reddit.notFirst = true;
                    didOnline = true;

                } catch (Exception e) {
                    e.printStackTrace();
                    if (e instanceof NetworkException) {
                        Toast.makeText(
                                        mContext,
                                        "Error "
                                                + ((NetworkException) e)
                                                        .getResponse()
                                                        .getStatusMessage()
                                                + ": "
                                                + (e).getMessage(),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }
            if (!single) authedOnce = true;

        } catch (Exception e) {
            // TODO fail

        }
    }
}
