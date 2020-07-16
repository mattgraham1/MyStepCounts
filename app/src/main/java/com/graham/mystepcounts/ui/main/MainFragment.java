package com.graham.mystepcounts.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.graham.mystepcounts.R;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment {
  private static final String TAG = MainFragment.class.getSimpleName();
  private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1;

  private TableLayout mTableLayout;
  private TextView mErrorTextView;
  private MainViewModel mViewModel;

  private final FitnessOptions mFitnessOptions = FitnessOptions.builder()
      .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .build();

  private final DataSource ESTIMATED_STEP_DELTAS = new DataSource.Builder()
      .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
      .setType(DataSource.TYPE_DERIVED)
      .setStreamName("estimated_steps")
      .setAppPackageName("com.google.android.gms")
      .build();

  public static MainFragment newInstance() {
    return new MainFragment();
  }

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Nullable @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.main_fragment, container, false);
    mViewModel = new ViewModelProvider(this).get(MainViewModel.class);

    mTableLayout = view.findViewById(R.id.tableLayout);
    mErrorTextView = view.findViewById(R.id.textViewErrorMessage);

    return view;
  }

  @Override public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
        // Google Fit access granted. Could remove this code, but it's nice to have when checking if
        // the permissions were granted.
      }
    }
  }

  @Override public void onResume() {
    super.onResume();

    GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(
        Objects.requireNonNull(getActivity()),
        mFitnessOptions);

    // Check Google Signin Permission
    if (!GoogleSignIn.hasPermissions(account, mFitnessOptions)) {
      GoogleSignIn.requestPermissions(this, GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, account,
          mFitnessOptions);
    } else {
      mTableLayout.removeAllViews();
      getDailyStepCountsFromGoogleFit(mFitnessOptions);
    }
  }

  @Override public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.toolbar_menu, menu);
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_sort) {
      // Get the data sorted properly
      if (mViewModel != null) {
        if (mViewModel.isDataSortedInDescendingOrder()) {
          mViewModel.sortMapInAscendingOrder();
        } else {
          mViewModel.sortMapInDescendingOrder();
        }

        buildTable();
      }
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  /***
   * Method to read daily step counts from Google Fit API.
   * @param fitnessOptions Fitness Option for Google Fit API
   */
  private void getDailyStepCountsFromGoogleFit(FitnessOptions fitnessOptions) {
    // Create the start and end times for the date range
    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    long endTime = cal.getTimeInMillis();
    cal.add(Calendar.DAY_OF_YEAR, -13);
    long startTime = cal.getTimeInMillis();

    final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    Log.d(TAG, "Date Start: " + dateFormat.format(startTime));
    Log.d(TAG, "Date End: " + dateFormat.format(endTime));

    DataReadRequest readRequest = new DataReadRequest.Builder()
        .aggregate(ESTIMATED_STEP_DELTAS, DataType.AGGREGATE_STEP_COUNT_DELTA)
        .bucketByTime(1, TimeUnit.DAYS)
        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
        .build();

    GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(
        Objects.requireNonNull(getActivity()), fitnessOptions);

    Fitness.getHistoryClient(getActivity(), account)
        .readData(readRequest)
        .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
          @Override public void onSuccess(DataReadResponse response) {
            mViewModel.clearData();
            showErrorMessage(false);

            if (!response.getBuckets().isEmpty()) {
              for (Bucket bucket : response.getBuckets()) {
                String stepCount = "0";
                Date bucketStart = new Date(bucket.getStartTime(TimeUnit.MILLISECONDS));
                Date bucketEnd = new Date(bucket.getEndTime(TimeUnit.MILLISECONDS));
                Log.d(TAG, "Bucket start / end times: " +  dateFormat.format(bucketStart)
                    + " - " + dateFormat.format(bucketEnd));

                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet set : dataSets) {
                  List<DataPoint> dataPoints = set.getDataPoints();
                  Log.d(TAG, "dataset: " + set.getDataType().getName());
                  for (DataPoint dp : dataPoints) {
                    Log.d(TAG, "datapoint: " + dp.getDataType().getName());
                    for (Field field : dp.getDataType().getFields()) {
                      stepCount = dp.getValue(field).toString();
                      Log.d(TAG, "Field: " + field.getName() + " Value: " + dp.getValue(field));
                    }
                  }
                }

                // Add the data
                if (mViewModel != null) {
                  mViewModel.addDailyStepCount(bucketStart, stepCount);
                }
              }

              // Update current day step count
              readDailyTotalSteps();
            }
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override public void onFailure(@NonNull Exception e) {
            Log.d(TAG, "OnFailure()", e);
            showErrorMessage(true);
          }
        });
  }

  /***
   * Method to read current days total step count.
   */
  private void readDailyTotalSteps() {
    GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(
        Objects.requireNonNull(getActivity()),
        mFitnessOptions);

    Fitness.getHistoryClient(getActivity(), account)
        .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
        .addOnSuccessListener(new OnSuccessListener<DataSet>() {
          @Override public void onSuccess(DataSet dataSet) {
            showErrorMessage(false);

            List<DataPoint> dataPoints = dataSet.getDataPoints();
            String dailyTotalStepCount = "0";
            for (DataPoint dp : dataPoints) {
              List<Field> fields = dp.getDataType().getFields();
              for (Field field : fields) {
                Log.d(TAG, "Field: " + field.getName() + " Value: " + dp.getValue(field));
                dailyTotalStepCount = dp.getValue(field).toString();
              }
            }

            // Add the data
            if (mViewModel != null) {
              mViewModel.addDailyStepCount(new Date(System.currentTimeMillis()), dailyTotalStepCount);
            }

            // Ensure we have the date in descending order and then build the table.
            if (mViewModel != null) {
              mViewModel.sortMapInDescendingOrder();
            }

            buildTable();
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override public void onFailure(@NonNull Exception e) {
            Log.d(TAG, "OnFailure()", e);
            showErrorMessage(true);
          }
        });
  }

  /***
   * Method to get the data from the viewmodel and then build the TableLayout.
   */
  private void buildTable() {
    final DateFormat dateFormat = DateFormat.getDateInstance();

    int index = 0;
    if (mViewModel != null) {
      mTableLayout.removeAllViews();
      for (Map.Entry<Date, String> entry : mViewModel.getFitnessData().entrySet()) {
        mTableLayout.addView(createTableRow(dateFormat.format(entry.getKey()), entry.getValue()), index);
        index++;
      }
    }
  }

  /***
   * Method to help dynamically rows, with two textviews inside of it.
   * @param date String to represent a Date
   * @param stepCount String to indicate number of steps to display
   * @return Newly create TableRow
   */
  private TableRow createTableRow(String date, String stepCount) {
    TableRow tableRow = new TableRow(getContext());
    TableRow.LayoutParams rowParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
        TableRow.LayoutParams.MATCH_PARENT);
    rowParams.setMargins(2,2,2,2);
    tableRow.setLayoutParams(rowParams);
    tableRow.setBackgroundColor(Color.BLACK);

    TableRow.LayoutParams textViewParams = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT);
    textViewParams.setMargins(2,2,2,2);
    TextView textViewDate = new TextView(getContext());
    textViewDate.setLayoutParams(textViewParams);
    textViewDate.setPadding(8, 8,8,8);
    textViewDate.setText(date);
    textViewDate.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    textViewDate.setBackgroundColor(Color.WHITE);

    TextView textViewCount = new TextView(getContext());
    textViewCount.setLayoutParams(textViewParams);
    textViewCount.setPadding(8, 8,8,8);
    textViewCount.setText(stepCount);
    textViewCount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    textViewCount.setBackgroundColor(Color.WHITE);

    tableRow.addView(textViewDate);
    tableRow.addView(textViewCount);
    return tableRow;
  }

  /***
   * Method to show an error message to the user.
   * @param show boolean value to indicate whether to show the error text
   */
  private void showErrorMessage(boolean show) {
    if (show) {
      mErrorTextView.setVisibility(View.VISIBLE);
      mTableLayout.setVisibility(View.INVISIBLE);
    } else {
      mTableLayout.setVisibility(View.VISIBLE);
      mErrorTextView.setVisibility(View.GONE);
    }
  }
}