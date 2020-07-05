package com.graham.mystepcounts.ui.main;

import androidx.lifecycle.ViewModel;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class MainViewModel extends ViewModel {
  private boolean mSortedDescendingOrder;
  private Map<Date, String> mDailyStepCountMap = new TreeMap<>();

  /***
   * Method to add new key-value into the fitness data map.
   * @param date Date for steps.
   * @param steps String representing the number of steps.
   */
  public void addDailyStepCount(Date date, String steps) {
    if (date == null) {
      return;
    }

    mDailyStepCountMap.put(date, steps.isEmpty() ? "0" : steps);
  }

  /***
   * Method to get the fitness data.
   * @return Map of data
   */
  public Map<Date, String> getFitnessData() {
    return mDailyStepCountMap;
  }

  /***
   * Method to indicate which sort order the data is in.
   * @return boolean
   */
  public boolean isDataSortedInDescendingOrder() {
    return mSortedDescendingOrder;
  }

  /***
   * Method to sort the data in ascending order.
   */
  public void sortMapInAscendingOrder() {
    mDailyStepCountMap = new TreeMap<>(mDailyStepCountMap);
    mSortedDescendingOrder = false;
  }

  /***
   * method to sort the data in descending order.
   */
  public void sortMapInDescendingOrder() {
    Map<Date, String> sortedMap = new TreeMap<>(Collections.reverseOrder());
    sortedMap.putAll(mDailyStepCountMap);
    mDailyStepCountMap = sortedMap;
    mSortedDescendingOrder = true;
  }
}