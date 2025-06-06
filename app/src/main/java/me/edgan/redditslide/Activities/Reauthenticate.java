package me.edgan.redditslide.Activities;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AlertDialog;

import com.afollestad.materialdialogs.MaterialDialog;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.LoggedInAccount;

import java.util.HashSet;
import java.util.Set;

/** Created by ccrama on 5/27/2015. */
public class Reauthenticate extends BaseActivityAnim {
    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        applyColorTheme("");
        setContentView(R.layout.activity_login);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, "Re-authenticate", true, true);

        String[] scopes = {
            "identity",
            "modcontributors",
            "modconfig",
            "modothers",
            "modwiki",
            "creddits",
            "livemanage",
            "account",
            "privatemessages",
            "modflair",
            "modlog",
            "report",
            "modposts",
            "modwiki",
            "read",
            "vote",
            "edit",
            "submit",
            "subscribe",
            "save",
            "wikiread",
            "flair",
            "history",
            "mysubreddits",
            "wikiedit"
        };
        final OAuthHelper oAuthHelper = Authentication.reddit.getOAuthHelper();
        final Credentials credentials =
                Credentials.installedApp(Constants.getClientId(), Constants.REDDIT_REDIRECT_URL);
        String authorizationUrl =
                oAuthHelper.getAuthorizationUrl(credentials, true, scopes).toExternalForm();
        authorizationUrl = authorizationUrl.replace("www.", "i.");
        authorizationUrl = authorizationUrl.replace("%3A%2F%2Fi", "://www");
        Log.v(LogUtil.getTag(), "Auth URL: " + authorizationUrl);
        final CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
        } else {
            cookieManager.removeAllCookie();
        }
        final WebView webView = (WebView) findViewById(R.id.web);

        webView.loadUrl(authorizationUrl);
        webView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public void onProgressChanged(WebView view, int newProgress) {
                        //                activity.setProgress(newProgress * 1000);
                    }
                });

        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        if (url.contains("code=")) {
                            Log.v(LogUtil.getTag(), "WebView URL: " + url);
                            // Authentication code received, prevent HTTP call from being made.
                            webView.stopLoading();
                            new UserChallengeTask(oAuthHelper, credentials).execute(url);
                            webView.setVisibility(View.GONE);
                            webView.clearCache(true);
                            webView.clearHistory();
                        }
                    }
                });
    }

    private final class UserChallengeTask extends AsyncTask<String, Void, OAuthData> {
        private final OAuthHelper mOAuthHelper;
        private final Credentials mCredentials;
        private MaterialDialog mMaterialDialog;

        public UserChallengeTask(OAuthHelper oAuthHelper, Credentials credentials) {
            Log.v(LogUtil.getTag(), "UserChallengeTask()");
            mOAuthHelper = oAuthHelper;
            mCredentials = credentials;
        }

        @Override
        protected void onPreExecute() {
            // Show a dialog to indicate progress
            MaterialDialog.Builder builder =
                    new MaterialDialog.Builder(Reauthenticate.this)
                            .title(R.string.login_authenticating)
                            .progress(true, 0)
                            .content(R.string.misc_please_wait)
                            .cancelable(false);
            mMaterialDialog = builder.build();
            mMaterialDialog.show();
        }

        @Override
        protected OAuthData doInBackground(String... params) {
            try {
                OAuthData oAuthData = mOAuthHelper.onUserChallenge(params[0], mCredentials);
                if (oAuthData != null) {
                    Authentication.reddit.authenticate(oAuthData);
                    Authentication.isLoggedIn = true;
                    String refreshToken = Authentication.reddit.getOAuthData().getRefreshToken();
                    SharedPreferences.Editor editor = Authentication.authentication.edit();
                    Set<String> accounts =
                            Authentication.authentication.getStringSet(
                                    "accounts", new HashSet<String>());
                    LoggedInAccount me = Authentication.reddit.me();
                    String toRemove = "";
                    for (String s : accounts) {
                        if (s.contains(me.getFullName())) {
                            toRemove = s;
                        }
                    }

                    if (!toRemove.isEmpty()) accounts.remove(toRemove);

                    accounts.add(me.getFullName() + ":" + refreshToken);
                    Authentication.name = me.getFullName();
                    editor.putStringSet("accounts", accounts);
                    Set<String> tokens =
                            Authentication.authentication.getStringSet(
                                    "tokens", new HashSet<String>());
                    tokens.add(refreshToken);
                    editor.putStringSet("tokens", tokens);
                    editor.putString("lasttoken", refreshToken);
                    editor.remove("backedCreds");
                    Reddit.appRestart.edit().remove("back").apply();
                    editor.apply();
                } else {
                    Log.e(LogUtil.getTag(), "Passed in OAuthData was null");
                }
                return oAuthData;
            } catch (IllegalStateException | NetworkException | OAuthException e) {
                // Handle me gracefully
                Log.e(LogUtil.getTag(), "OAuth failed");
                Log.e(LogUtil.getTag(), e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(OAuthData oAuthData) {
            // Dismiss old progress dialog
            mMaterialDialog.dismiss();

            new AlertDialog.Builder(Reauthenticate.this)
                    .setTitle(R.string.reauth_complete)
                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> finish())
                    .setCancelable(false)
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }
    }
}
