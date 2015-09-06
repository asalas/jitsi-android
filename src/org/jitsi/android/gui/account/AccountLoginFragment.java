/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.android.gui.account;

import java.util.*;

import net.java.sip.communicator.service.protocol.*;
import net.voipmedia.SubscriberConsole;

import org.jitsi.*;
import org.jitsi.android.gui.util.*;
import org.jitsi.service.osgi.*;

import android.accounts.*;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import org.apache.http.Header;
import org.jitsi.android.JitsiApplication;
import org.jitsi.service.libjitsi.LibJitsi;
import org.json.JSONObject;

/**
 * The <tt>AccountLoginFragment</tt> is used for creating new account, but can
 * be also used to obtain user credentials. In order to do that parent
 * <tt>Activity</tt> must implement {@link AccountLoginListener}.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 */
public class AccountLoginFragment
    extends OSGiFragment
{
    /**
     * The username property name.
     */
    public static final String ARG_USERNAME = "Username";

    /**
     * The password property name.
     */
    public static final String ARG_PASSWORD = "Password";

    /**
     * The listener parent Activity that will be notified when user enters
     * login and password.
     */
    private AccountLoginListener loginListener;
    
    // TODO: cambios asalas
    private static final String RESPONSE_OK = "OK";

    private String AUTH_USER;
    private String AUTH_PASSWORD;
    private String PATH_GET_SUBSCRIBER;

    private AsyncHttpClient restClient;
    private RequestParams restRequestParams;

    private Map<String, Object> subscriberConsoleMap;

    private ProgressDialog progressDialog;

    // Constants for selected networks
    private static final String SIP_NETWORK = "SIP";
    private static final String JABBER_NETWORK = "Jabber";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        if(activity instanceof AccountLoginListener)
        {
            this.progressDialog = new ProgressDialog(activity);
            this.progressDialog.setTitle("Authenticating ...");
            this.progressDialog.setMessage("Please wait to response from ConsoleVoIPMedia");
            this.progressDialog.setCancelable(false);
            
            this.loginListener = (AccountLoginListener)activity;
        }
        else
        {
            throw new RuntimeException("Account login listener unspecified");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach()
    {
        super.onDetach();

        loginListener = null;
    }

    /**
     *  {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View content = inflater.inflate(R.layout.new_account, container, false);

//        Spinner spinner = (Spinner) content.findViewById(R.id.networkSpinner);

        ArrayAdapter<CharSequence> adapter
                = ArrayAdapter.createFromResource(
                        getActivity(),
                        R.array.networks_array,
                        R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(R.layout.dropdown_spinner_item);

//        spinner.setAdapter(adapter);

        initSignInButton(content);

        Bundle extras = getArguments();
        if (extras != null)
        {
            String username = extras.getString(ARG_USERNAME);

            if (username != null && username.length() > 0)
            {
                ViewUtil.setTextViewValue(
                        container, R.id.usernameField, username);
            }

            String password = extras.getString(ARG_PASSWORD);

            if (password != null && password.length() > 0)
            {
                ViewUtil.setTextViewValue(
                        content, R.id.passwordField, password);
            }
        }

        return content;
    }

    /**
     * Initializes the sign in button.
     */
    private void initSignInButton(final View content)
    {
        final Button signInButton
            = (Button) content.findViewById(R.id.signInButton);
        signInButton.setEnabled(true);

        signInButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                final EditText userNameField
                    = (EditText) content.findViewById(R.id.usernameField);
                final EditText passwordField
                    = (EditText) content.findViewById(R.id.passwordField);

                // TODO: cambios asalas
                progressDialog.show();

                final String login = userNameField.getText().toString();
                final String password = passwordField.getText().toString();

                // Invoke the WS ConsoleVoIPMedia/REST
                AUTH_USER = LibJitsi.getConfigurationService().getString("net.voipmedia.console.auth.user");
                AUTH_PASSWORD = LibJitsi.getConfigurationService().getString("net.voipmedia.console.auth.password");
                PATH_GET_SUBSCRIBER = LibJitsi.getConfigurationService().getString("net.voipmedia.console.path.get.subscriber");

                restRequestParams = new RequestParams();
                restRequestParams.put("username", login);
                restRequestParams.put("password", password);

                restClient = new AsyncHttpClient();
                restClient.setTimeout(4000);                
                restClient.setBasicAuth(AUTH_USER, AUTH_PASSWORD);
                restClient.setAuthenticationPreemptive(true);                
                
                JitsiApplication.setSubscriberConsoleMap(null);
                
                restClient.get(PATH_GET_SUBSCRIBER, restRequestParams, new JsonHttpResponseHandler()
                {
                    @Override
                    public void onSuccess(int i, Header[] headers, JSONObject response)
                    {
                        // Hide the Progress Dialog
                        progressDialog.hide();
                        try
                        {
                            String statusCode = response.getString("statusCode");
                            if (statusCode.equals(RESPONSE_OK))
                            {
                                JSONObject jsonSubscriber = response.getJSONObject("subscriber");
                                subscriberConsoleMap = new HashMap<String, Object>();
                                subscriberConsoleMap.put(SubscriberConsole.USER_NAME, jsonSubscriber.getString(SubscriberConsole.USER_NAME));
                                subscriberConsoleMap.put(SubscriberConsole.DOMAIN, jsonSubscriber.getString(SubscriberConsole.DOMAIN));
                                subscriberConsoleMap.put(SubscriberConsole.PASSWORD, jsonSubscriber.getString(SubscriberConsole.PASSWORD));
                                subscriberConsoleMap.put(SubscriberConsole.USER_ID_CONSOLE, jsonSubscriber.getInt(SubscriberConsole.USER_ID_CONSOLE));
                                subscriberConsoleMap.put(SubscriberConsole.PBX_HOST, jsonSubscriber.getString(SubscriberConsole.PBX_HOST));
                                subscriberConsoleMap.put(SubscriberConsole.HA1B, jsonSubscriber.getBoolean(SubscriberConsole.HA1B));
                            }
                            if (subscriberConsoleMap != null)
                            {
                                JitsiApplication.setSubscriberConsoleMap(subscriberConsoleMap);
                                
                                final String _userName = subscriberConsoleMap.get(SubscriberConsole.USER_NAME).toString();
                                final String _pbxHostIpName = subscriberConsoleMap.get(SubscriberConsole.PBX_HOST).toString();
                                final String _login = _userName+"@"+_pbxHostIpName;
                                final String _password = subscriberConsoleMap.get(SubscriberConsole.PASSWORD).toString();

                                // register SIP account
                                loginListener.onLoginPerformed(_login, _password, SIP_NETWORK);

                                // register Jabber account
                                loginListener.onLoginPerformed(_login, _password, JABBER_NETWORK);
                            }
                            else
                            {
                                loginListener.onLoginFailed();
                            }
                        }
                        catch (Exception e)
                        {
                            loginListener.onLoginFailed();
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable)
                    {
                        // Hide the Progress Dialog
                        progressDialog.hide();
                        loginListener.onLoginFailed();
                    }
                });
            }
        });
    }

    /**
     * Stores the given <tt>protocolProvider</tt> data in the android system
     * accounts.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>,
     * corresponding to the account to store
     */
    private void storeAndroidAccount(ProtocolProviderService protocolProvider)
    {
        Map<String, String> accountProps
            = protocolProvider.getAccountID().getAccountProperties();

        String username = accountProps.get(ProtocolProviderFactory.USER_ID);

        Account account
            = new Account(  username,
                            getString(R.string.ACCOUNT_TYPE));

        final Bundle extraData = new Bundle();
        for (String key : accountProps.keySet())
        {
            extraData.putString(key, accountProps.get(key));
        }

        AccountManager am = AccountManager.get(getActivity());
        boolean accountCreated
            = am.addAccountExplicitly(
                account,
                accountProps.get(ProtocolProviderFactory.PASSWORD), extraData);

        Bundle extras = getArguments();
        if (extras != null)
        {
            if (accountCreated)
            {  //Pass the new account back to the account manager
                AccountAuthenticatorResponse response
                    = extras.getParcelable(
                        AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

                Bundle result = new Bundle();
                result.putString(   AccountManager.KEY_ACCOUNT_NAME,
                                    username);
                result.putString(   AccountManager.KEY_ACCOUNT_TYPE,
                                    getString(R.string.ACCOUNT_TYPE));
                result.putAll(extraData);

                response.onResult(result);
            }
            // TODO: notify about account authentication
            //finish();
        }
    }

    /**
     * Creates new <tt>AccountLoginFragment</tt> with optionally filled login
     * and password fields(pass <tt>null</tt> arguments to omit).
     *
     * @param login optional login text that will be filled on the form.
     * @param password optional password text that will be filled on the form.
     *
     * @return new instance of parametrized <tt>AccountLoginFragment</tt>.
     */
    public static AccountLoginFragment createInstance(String login,
                                                      String password)
    {
        AccountLoginFragment fragment = new AccountLoginFragment();

        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, login);
        args.putString(ARG_PASSWORD, password);

        return fragment;
    }

    /**
     * The interface is used to notify listener when user click the sign-in
     * button.
     */
    public interface AccountLoginListener
    {
        /**
         * Method is called when user click the sign in button.
         * @param login the login entered by the user.
         * @param password the password entered by the user.
         * @param network the network name selected by the user.
         */
        public void onLoginPerformed( String login,
                                      String password,
                                      String network);
        public void onLoginFailed();
    }
}
