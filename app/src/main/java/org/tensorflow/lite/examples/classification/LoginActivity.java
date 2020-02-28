package org.tensorflow.lite.examples.classification;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class LoginActivity extends AppCompatActivity {

    private EditText mUsernameView;
    private GoogleSignInClient mGoogleSignInClient;
    private String mUsername;
    private String mRoom;
    private int RC_SIGN_IN = 1;
    private Socket mSocket;

    @Override
    public void onBackPressed(){
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        ChatApplication app =  (ChatApplication) this.getApplication();
        mSocket = app.getSocket();
        mSocket.connect();
        Log.i("Info", "Oncreate");
        // Set up the login form.
        mUsername = "Android";
        mUsernameView = (EditText) findViewById(R.id.username_input);
        mUsernameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button signInButton = (Button) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
        try {
            Intent intent = getIntent();
            String action = intent.getAction();
            Uri data = intent.getData();
            URLlogin(data);
        }catch(Exception e){
            Log.i("opened by app", "appopen" );
        }
        mSocket.on("login", onLogin);
        mSocket.on("loginfailed",onLoginFailed);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocket.off("loginfailed",onLoginFailed);
        mSocket.off("login", onLogin);
        finishAndRemoveTask();
    }

    /**
     * Attempts to sign in the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mSocket.connect();
        mUsernameView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString().trim();

        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            mUsernameView.setError(getString(R.string.error_field_required));
            mUsernameView.requestFocus();
            return;
        }
        Log.i("attempt login", ""+mSocket.connected());

        mRoom = username;

        // perform the user login attempt.
        try {
            JSONObject data = new JSONObject("{ \'username\' : \'"+mUsername+"\' ,\n\'room\':\'"+mRoom+"\' }");
            Log.i("JSON Button", data.toString());
            mSocket.emit("join", data);
        }catch (JSONException e){
            Log.i("JSON error", "attemptLogin: ");
        }

    }

    private void URLlogin(Uri link) {
        // Reset errors.
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(account);
        mRoom = link.getQueryParameter("user");
        // perform the user login attempt.
        try {
            JSONObject data = new JSONObject("{ \'username\' : \'"+mUsername+"\' ,\n\'room\':\'"+mRoom+"\' }");
            Log.i("JSON URL", data.toString());
            mSocket.emit("join", data);
        }catch (JSONException e){
            Log.i("JSON error", "attemptLogin: ");
        }

    }

    private Emitter.Listener onLogin = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            Log.i("Listener", data.toString());
            int numUsers;
            try {
                numUsers = data.getInt("numUsers");
                Log.i("Number", ""+data);
            } catch (JSONException e) {
                loginFailure();
                return;
            }
            loginSuccess(numUsers);
            Log.i("Login Success", "lesgo");
        }

    };
    private Emitter.Listener onLoginFailed = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            loginFailure();
            Log.i("Login Failed", "Error");
        }
    };


    protected void loginSuccess(int numUsers){
        Intent intent = new Intent(this,ClassifierActivity.class);
        intent.putExtra("username", mUsername);
        intent.putExtra("numUsers", numUsers);

        startActivity(intent);
    }

    protected void loginFailure(){
        Toast.makeText(this.getApplicationContext(),
                "Login Error", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart(){
        super.onStart();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(account);
    }

    public void updateUI(GoogleSignInAccount googleSignInAccount){
        Log.i("Account", "updateUI: "+googleSignInAccount);
        if (googleSignInAccount != null) {
            mUsername = googleSignInAccount.getDisplayName();
            Log.i("Success", "updateUI: "+mUsername);
        }else{
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            updateUI(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w("Failed sign in", "signInResult:failed code=" + e.getStatusCode());

        }
    }
}
