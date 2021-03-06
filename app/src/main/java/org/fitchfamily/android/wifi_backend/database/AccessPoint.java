package org.fitchfamily.android.wifi_backend.database;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.util.SimpleLocation;

import java.util.ArrayList;
import java.util.List;

@AutoValue
public abstract class AccessPoint {
    private static final int MIN_SAMPLES = 3;
    private static final String TAG = "WiFiBackendAP";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    AccessPoint() {

    }

    public static String bssid(String bssid) {
        return bssid.replace(":", "");
    }

    public static String readableBssid(String bssid) {
        return bssid(bssid).replaceAll(".(?!$).(?!$)", "$0:");
    }

    public static Builder builder() {
        return new AutoValue_AccessPoint.Builder();
    }

    public abstract String rfId();
    @Nullable
    public abstract String ssid();
    public abstract ImmutableList<SimpleLocation> samples();
    public abstract int moveGuard();
    public abstract int rfType();

    public SimpleLocation sample(int index) {
        if(index < samples().size()) {
            return samples().get(index);
        } else {
            return null;
        }
    }

    /**
     * Use this function to get the estimate location
     * @return the estimate location or null if no samples are available
     */
    public SimpleLocation estimateLocation() {
        if(samples().size() == 0) {
            return null;
        }

        // get center of points

        double latitude = 0.0;
        double longitude = 0.0;

        for(SimpleLocation sample : samples()) {
            latitude += sample.latitude();
            longitude += sample.longitude();
        }

        latitude /= (double) samples().size();
        longitude /= (double) samples().size();

        SimpleLocation center = SimpleLocation.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radius(-1.0f)
                .changed(false)
                .build();

        // get biggest distance

        float radius = 0.0f;

        for(SimpleLocation sample : samples()) {
            radius = Math.max(radius, center.distanceTo(sample));
        }


        return SimpleLocation.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radius(radius)
                .changed(false)
                .build();
    }

    public abstract Builder buildUpon();

    private static float perimeter(List<SimpleLocation> samples) {
        float result = 0.0f;

        for (SimpleLocation sample1 : samples) {
            for (SimpleLocation sample2 : samples) {
                result += sample1.distanceTo(sample2);
            }
        }

        return result;
    }

    public float perimeter() {
        return perimeter(samples());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder rfId(String rfId);
        public abstract Builder ssid(String ssid);
        public abstract Builder samples(List<SimpleLocation> samples);
        public abstract Builder moveGuard(int moveGuard);
        public abstract Builder rfType(int rfType);
        public abstract AccessPoint build();

        protected abstract String rfId();
        protected abstract int moveGuard();
        protected abstract ImmutableList<SimpleLocation> samples();
        protected abstract int rfType();

        protected int samplesCount() {
            try {
                return samples().size();
            } catch (Exception ex) {
                return 0;
            }
        }

        public Builder moved(int movedGuardCount) {
            return moveGuard(movedGuardCount);
        }

        public Builder decMoved() {
            return moveGuard(Math.max(0, moveGuard()));
        }

        public Builder addSample(SimpleLocation location) {
            return addSample(location, MIN_SAMPLES);
        }

        public Builder addSample(SimpleLocation location, int maxSamples) {
            maxSamples = Math.max(maxSamples, MIN_SAMPLES);

            if(samplesCount() < maxSamples) {
                List<SimpleLocation> samples = new ArrayList<>();

                if(samplesCount() != 0) {
                    samples.addAll(samples());
                }

                samples.add(location);
                if (DEBUG) {
                    Log.i(TAG, "Simple add to " + rfId() + ", add " + location + ", result="+samples);
                }

                return samples(samples);
            } else {
                // We will take the new sample an see if we can make a triangle with
                // a larger perimeter by replacing one of our current samples with
                // the new one.

                List<SimpleLocation> bestSamples = samples();
                float bestPerimeter = perimeter(bestSamples);

                for (int i = 0; i < samples().size(); i++) {
                    List<SimpleLocation> samples = new ArrayList<SimpleLocation>(samples());
                    samples.set(i, location);

                    float guessPerimeter = perimeter(samples);

                    if (guessPerimeter > bestPerimeter) {
                        bestSamples = samples;
                        bestPerimeter = guessPerimeter;

                        if (DEBUG) {
                            Log.i(TAG, "Better perimeter point found on " + rfId() + ", i=" + i);
                        }
                    }
                }

                return samples(bestSamples);
            }
        }

        public Builder clearSamples() {
            return samples(new ArrayList<SimpleLocation>());
        }
    }
}
