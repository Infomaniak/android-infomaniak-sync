package com.infomaniak.sync;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.CardView;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dd.processbutton.iml.ActionProcessButton;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.Date;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.AccountsActivity;
import at.bitfire.davdroid.ui.setup.DavResourceFinder;
import at.bitfire.davdroid.ui.setup.LoginInfo;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class InfomaniakLogin extends Activity {

    private OkHttpClient okHttpClient;
    public static String URL_SERVER_LOGIN = "https://login.infomaniak.com/token";
    private final static String URL_API_PROFIL = "https://api.infomaniak.com/1/profile";
    private final static String URL_API_PROFIL_PASSWORD = "https://api.infomaniak.com/1/profile/password";


    private EditText login;
    private EditText password;
    private EditText editOtp;
    private ActionProcessButton btnSignIn;
    private LinearLayout layoutOtp;
    private TextInputLayout loginWrapper;
    private TextInputLayout passwordWrapper;
    private TextInputLayout editOtpWrapper;
    private CardView loginCard;
    private View loadingView;

    private String userLogin;
    private String userPassword;
    private String userOtp;

    private NetworkChangeReceiver networkChangeReceiver;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Stetho.initializeWithDefaults(this);

        okHttpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new StethoInterceptor())
                .build();

        setContentView(R.layout.infomaniak_login);

        loadingView = findViewById(R.id.loading_view);

        loginCard = findViewById(R.id.login_card);
        loginWrapper = findViewById(R.id.edit_login_wrapper);
        login = findViewById(R.id.edit_login);
        passwordWrapper = findViewById(R.id.edit_password_wrapper);
        password = findViewById(R.id.edit_password);
        layoutOtp = findViewById(R.id.layout_otp);
        editOtpWrapper = findViewById(R.id.edit_otp_wrapper);
        editOtp = findViewById(R.id.edit_otp);

        TextView.OnEditorActionListener imeAction = new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_DONE)) {
                    if (btnSignIn.isClickable()) {
                        btnSignIn.callOnClick();
                    }
                }
                return false;
            }
        };
        password.setOnEditorActionListener(imeAction);
        editOtp.setOnEditorActionListener(imeAction);

        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setMode(ActionProcessButton.Mode.ENDLESS);

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginWrapper.setErrorEnabled(false);
                passwordWrapper.setErrorEnabled(false);
                editOtpWrapper.setErrorEnabled(false);

                userLogin = login.getText().toString();
                userPassword = password.getText().toString();
                userOtp = editOtp.getText().toString();

                if (userLogin.isEmpty()) {
                    loginWrapper.setError(getString(R.string.login_empty));
                } else if (userPassword.isEmpty()) {
                    passwordWrapper.setError(getString(R.string.password_empty));
                } else if (layoutOtp.getVisibility() == View.VISIBLE && userOtp.isEmpty()) {
                    editOtpWrapper.setError(getString(R.string.mandatoryField));
                } else {
                    new LoginTask().execute();
                }
            }
        });

        if (false) {
            launchApp();
        } else {
            networkChangeReceiver = new NetworkChangeReceiver(new HandlerNetworkStatus());
            registerReceiver(networkChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

            SharedPreferences sharedPref = getSharedPreferences("lastUser", Context.MODE_PRIVATE);
            login.setText(sharedPref.getString("email", ""));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (layoutOtp.getVisibility() == View.VISIBLE) {
            try {
                ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipBoard != null) {
                    ClipData clipData = clipBoard.getPrimaryClip();
                    ClipData.Item item = clipData.getItemAt(0);
                    String code = item.getText().toString();
                    try {
                        Integer.valueOf(code);
                        editOtp.setText(code);
                        if (btnSignIn.isClickable()) {
                            btnSignIn.callOnClick();
                        }
                    } catch (NumberFormatException ignored) {

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {

        try {
            if (networkChangeReceiver != null)
                unregisterReceiver(networkChangeReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();

    }

    private void launchApp() {
        Intent i = new Intent(this, AccountsActivity.class);
        if (true) {
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        startActivity(i);
        //you can die so
        finish();
    }


    @SuppressLint("HandlerLeak")
    private class HandlerNetworkStatus extends Handler {
        @Override
        public void handleMessage(Message message) {

            Bundle bundle = message.getData();

            if (bundle.getBoolean("isNetworking")) {
                btnSignIn.setErrorText(null);
                btnSignIn.setProgress(0);
                btnSignIn.setEnabled(true);
            } else {
                btnSignIn.setErrorText(getString(R.string.no_internet_connection));
                btnSignIn.setProgress(-1);
                btnSignIn.setEnabled(false);
            }
        }
    }

    private class LoginTask extends AsyncTask<String, Void, ApiToken> {

        @Override
        protected ApiToken doInBackground(String... params) {
            try {

                Gson gson = new Gson();

                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("grant_type", "password")
                        .addFormDataPart("client_id", client_id)
                        .addFormDataPart("client_secret", client_secret)
                        .addFormDataPart("username", userLogin)
                        .addFormDataPart("password", userPassword)
                        .addFormDataPart("access_type", "offline");

                if (!userOtp.isEmpty()) {
                    builder.addFormDataPart("double_auth_code", userOtp);
                    builder.addFormDataPart("method", "otp");
                }

                RequestBody requestBody = builder.build();

                Request request = new Request.Builder()
                        .url(URL_SERVER_LOGIN)
                        //.header("X_FORCE_DOUBLE_AUTH", "1")
                        .post(requestBody)
                        .build();

                Response response = okHttpClient.newCall(request).execute();

                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    return null;
                }
                String bodyResult = responseBody.string();

                ApiToken apiToken;
                if (bodyResult != null) {
                    JsonElement jsonResult = new JsonParser().parse(bodyResult);
                    if (response.isSuccessful()) {
                        apiToken = gson.fromJson(jsonResult.getAsJsonObject(), ApiToken.class);
                    } else {
                        if (!jsonResult.isJsonNull() && jsonResult.isJsonObject()) {
                            ErrorAPI error = gson.fromJson(jsonResult.getAsJsonObject(), ErrorAPI.class);
                            return new ApiToken(error);
                        } else {
                            return null;
                        }
                    }
                } else {
                    return null;
                }

                request = new Request.Builder()
                        .url(URL_API_PROFIL)
                        .header("Authorization", "Bearer " + apiToken.getAccess_token())
                        .get()
                        .build();

                response = okHttpClient.newCall(request).execute();

                responseBody = response.body();

                if (responseBody == null) {
                    return null;
                }
                bodyResult = responseBody.string();

                if (response.isSuccessful() && bodyResult != null) {
                    JsonElement jsonResult = new JsonParser().parse(bodyResult);
                    InfomaniakUser infomaniakUser = gson.fromJson(jsonResult.getAsJsonObject().getAsJsonObject("data"), InfomaniakUser.class);

                    builder = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("name", "InfomaniakSync_" + new Date());

                    request = new Request.Builder()
                            .url(URL_API_PROFIL_PASSWORD)
                            .header("Authorization", "Bearer " + apiToken.getAccess_token())
                            .post(builder.build())
                            .build();

                    response = okHttpClient.newCall(request).execute();

                    responseBody = response.body();

                    if (responseBody == null) {
                        return null;
                    }

                    bodyResult = responseBody.string();
                    if (response.isSuccessful() && bodyResult != null) {
                        jsonResult = new JsonParser().parse(bodyResult);
                        InfomaniakPassword infomaniakPassword = gson.fromJson(jsonResult.getAsJsonObject().getAsJsonObject("data"), InfomaniakPassword.class);

                        LoginInfo loginInfo = new LoginInfo(new URI("https://sync.infomaniak.com"), infomaniakUser.getLogin(), infomaniakPassword.getPassword(), null);
                        DavResourceFinder.Configuration configuration = new DavResourceFinder(InfomaniakLogin.this, loginInfo).findInitialConfiguration();
                    }
                }
                return null;
            } catch (Exception exception) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(ApiToken apiToken) {
            try {
                if (apiToken != null) {
                    ErrorAPI errorAPI = apiToken.getErrorAPI();
                    if (errorAPI == null) {

//                        mainActivity.addCredential(apiToken);


                        btnSignIn.setProgress(100);

                        Animator animator =
                                ViewAnimationUtils.createCircularReveal(loginCard, loginCard.getWidth() / 2, loginCard.getHeight() / 2, 1f * loginCard.getHeight(), 0F);
                        animator.setInterpolator(new AccelerateDecelerateInterpolator());
                        animator.setDuration(500);
                        animator.setStartDelay(100);
                        animator.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animator) {

                            }

                            @Override
                            public void onAnimationEnd(Animator animator) {
                                loginCard.setVisibility(View.GONE);
                                launchApp();
                            }

                            @Override
                            public void onAnimationCancel(Animator animator) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animator) {

                            }
                        });
                        animator.start();
                    } else if (errorAPI.getError().equals("invalid_grant")) {
                        switch (errorAPI.getReason()) {
                            case "need_otp":
                                btnSignIn.setProgress(0);
                                btnSignIn.setEnabled(true);
                                editOtp.setEnabled(true);
                                layoutOtp.setVisibility(View.VISIBLE);
                                loadingView.setVisibility(View.GONE);
                                break;
                            case "otp_failed":
                                editOtpWrapper.setError(getString(R.string.bad_login));
                                btnSignIn.setErrorText(getString(R.string.bad_login));
                                editOtp.setEnabled(true);
                                enabledView();
                                layoutOtp.setVisibility(View.VISIBLE);
                                break;
                            default:
                                loginWrapper.setError(getString(R.string.bad_login));
                                passwordWrapper.setError(getString(R.string.bad_login));
                                btnSignIn.setErrorText(getString(R.string.bad_login));
                                enabledView();
                                break;
                        }
                    }
                } else {
                    btnSignIn.setErrorText(getString(R.string.error_title));
                    enabledView();
                }
            } catch (Exception e) {
                e.printStackTrace();
                btnSignIn.setErrorText(getString(R.string.error_title));
                enabledView();
            }
        }

        private void enabledView() {
            layoutOtp.setVisibility(View.GONE);
            editOtp.setText("");
            btnSignIn.setProgress(-1);
            btnSignIn.setEnabled(true);
            login.setEnabled(true);
            password.setEnabled(true);
            editOtp.setEnabled(true);
            loadingView.setVisibility(View.GONE);
        }

        @Override
        protected void onPreExecute() {
            loadingView.setVisibility(View.VISIBLE);
            loginWrapper.setErrorEnabled(false);
            passwordWrapper.setErrorEnabled(false);
            editOtpWrapper.setErrorEnabled(false);

            btnSignIn.setEnabled(false);
            btnSignIn.setProgress(1);

            login.setEnabled(false);
            password.setEnabled(false);
            editOtp.setEnabled(false);
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }
}
