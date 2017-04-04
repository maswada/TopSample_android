package jp.mediba.topsample;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED;
import static java.text.DateFormat.getDateInstance;
import static java.text.DateFormat.getDateTimeInstance;

public class MainActivity extends FragmentActivity {

    public static final String TAG = "TopSample";
    public static GoogleApiClient mClient = null;
    private boolean done = false;
    private Integer currentSteps = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateButtonText(0);

        Button button = (Button)this.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ContentActivity.class);
                intent.putExtra("STEPS", currentSteps);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        if (mClient != null) {
            mClient.stopAutoManage(MainActivity.this);
            mClient.disconnect();
        }

        this.buildGoogleApiClient();

    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        mClient.stopAutoManage(MainActivity.this);
        mClient.disconnect();
    }


    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mClient);
        startActivityForResult(signInIntent, 1);
    }

    private void stepsButtonAction() {
        Log.i(TAG, "button tapped");
    }


    private void updateButtonText(Integer steps) {
        this.currentSteps = steps;
        Button button = (Button)this.findViewById(R.id.button);
        button.setText("今日の歩数：" + this.currentSteps.toString());
    }

    private void buildGoogleApiClient() {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(Scopes.FITNESS_ACTIVITY_READ), new Scope(Scopes.FITNESS_LOCATION_READ))
                .build();

        mClient = new GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, options)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected.");

                                new ReadDataTask(MainActivity.this).execute();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, null)
                .build();
    }

    private class ReadDataTask extends AsyncTask<Void, Integer, Integer> {

        MainActivity mainActivity;
        public ReadDataTask(MainActivity activity) {
            super();
            mainActivity = activity;
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
//            mainActivity.updateButtonText(2222);
            mainActivity.updateButtonText(values[0]);
            Log.i("test", "onProgressUpdate");
        }


        @Override
        protected void onPostExecute(Integer result) {
            if (result == 1001) {
                Log.i(TAG, "onPostExecute 1001");
                mainActivity.signIn();
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            // [START build_read_data_request]
            // Setting a start and end date using a range of 1 week before this moment.
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            cal.set(Calendar.HOUR_OF_DAY, 24);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar. HOUR_OF_DAY, -24);

            long startTime = cal.getTimeInMillis();

            java.text.DateFormat dateFormat = getDateInstance();
            Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
            Log.i(TAG, "Range End: " + dateFormat.format(endTime));

            Intent intent = getIntent();
            String action = intent.getAction();
            String type = intent.getType();
            String supportedType = DataType.getMimeType(DataType.TYPE_STEP_COUNT_DELTA);

            DataSource ds = null;
            if (Intent.ACTION_VIEW.equals(action) &&
                    supportedType.compareTo(type) == 0) {
                // Get the intent extras
                ds = DataSource.extract(intent);
            } else {
                ds = new DataSource.Builder()
                        .setAppPackageName("com.google.android.gms")
                        .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                        .setType(DataSource.TYPE_DERIVED)
                        .setStreamName("estimated_steps")
                        .build();
            }

            final DataReadRequest readRequest = new DataReadRequest.Builder()
                    .aggregate(ds, DataType.TYPE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();
            // [START read_dataset]
            // Invoke the History API to fetch the data with the query and await the result of
            // the read request.
            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            // [END read_dataset]
            if (dataReadResult.getStatus().getStatusCode() == SIGN_IN_REQUIRED) {
                return 1001;
            }

            publishProgress(getSteps(dataReadResult));
            return 0;
        }


    }


    private Integer getSteps(DataReadResult result) {
        Integer steps = 0;
        DateFormat dateFormat = getDateTimeInstance();
        if (result.getBuckets().size() > 0) {
            for (Bucket bucket : result.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    for (DataPoint dp : dataSet.getDataPoints()) {
                        Log.i(TAG, "Data point:");
                        Log.i(TAG, "\tType: " + dp.getDataType().getName());
                        Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                        Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));

                        for(Field field : dp.getDataType().getFields()) {
                            Log.i(TAG, "\tField: " + field.getName() + " Value: " + dp.getValue(field));
                            steps += dp.getValue(field).asInt();
                        }
                    }
                }
            }
        }
        return steps;
    }

    public static void printData(DataReadResult dataReadResult) {
        // [START parse_read_data_result]
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size() + " " + dataReadResult.getDataSets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                dumpDataSet(dataSet);
            }
        }
        // [END parse_read_data_result]
    }

    // [START parse_dataset]
    private static void dumpDataSet(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
//        DateFormat dateFormat = getTimeInstance();
        DateFormat dateFormat = getDateTimeInstance();
        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));

            for(Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));
            }
        }
    }
}
