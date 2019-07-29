/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package p.android.support.v4.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import p.android.support.v4.content.ContextCompat;
import p.android.support.v4.content.IntentCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Utility class for constructing synthetic back stacks for cross-task navigation
 * on Android 3.0 and newer.
 *
 * <p>In API level 11 (Android 3.0/Honeycomb) the recommended conventions for
 * app navigation using the back key changed. The back key's behavior is local
 * to the current task and does not capture navigation across different tasks.
 * Navigating across tasks and easily reaching the previous task is accomplished
 * through the "recents" UI, accessible through the software-provided Recents key
 * on the navigation or system bar. On devices with the older hardware button configuration
 * the recents UI can be accessed with a long press on the Home key.</p>
 *
 * <p>When crossing from one task stack to another post-Android 3.0,
 * the application should synthesize a back stack/history for the new task so that
 * the user may navigate out of the new task and back to the Launcher by repeated
 * presses of the back key. Back key presses should not navigate across task stacks.</p>
 *
 * <p>TaskStackBuilder provides a backward-compatible way to obey the correct conventions
 * around cross-task navigation on the device's version of the platform. On devices running
 * Android 3.0 or newer, calls to the {@link #startActivities()} method or sending the
 * {@link PendingIntent} generated by {@link #getPendingIntent(int, int)} will construct
 * the synthetic back stack as prescribed. On devices running older versions of the platform,
 * these same calls will invoke the topmost activity in the supplied stack, ignoring
 * the rest of the synthetic stack and allowing the back key to navigate back to the previous
 * task.</p>
 *
 * <div class="special reference">
 * <h3>About Navigation</h3>
 * For more detailed information about tasks, the back stack, and navigation design guidelines,
 * please read
 * <a href="{@docRoot}guide/topics/fundamentals/tasks-and-back-stack.html">Tasks and Back Stack</a>
 * from the developer guide and <a href="{@docRoot}design/patterns/navigation.html">Navigation</a>
 * from the design guide.
 * </div>
 */
public final class TaskStackBuilder implements Iterable<Intent> {
    private static final String TAG = "TaskStackBuilder";

    public interface SupportParentable {
        Intent getSupportParentActivityIntent();
    }

    interface TaskStackBuilderImpl {
        PendingIntent getPendingIntent(Context context, Intent[] intents, int requestCode,
                                       int flags, Bundle options);
    }

    static class TaskStackBuilderImplBase implements TaskStackBuilderImpl {
        public PendingIntent getPendingIntent(Context context, Intent[] intents, int requestCode,
                int flags, Bundle options) {
            Intent topIntent = new Intent(intents[intents.length - 1]);
            topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return PendingIntent.getActivity(context, requestCode, topIntent, flags);
        }
    }

    static class TaskStackBuilderImplHoneycomb implements TaskStackBuilderImpl {
        public PendingIntent getPendingIntent(Context context, Intent[] intents, int requestCode,
                int flags, Bundle options) {
            intents[0] = new Intent(intents[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
                    IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
            return TaskStackBuilderHoneycomb.getActivitiesPendingIntent(context, requestCode,
                    intents, flags);
        }
    }

    static class TaskStackBuilderImplJellybean implements TaskStackBuilderImpl {
        public PendingIntent getPendingIntent(Context context, Intent[] intents, int requestCode,
                int flags, Bundle options) {
            intents[0] = new Intent(intents[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
                    IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
            return TaskStackBuilderJellybean.getActivitiesPendingIntent(context, requestCode,
                    intents, flags, options);
        }
    }

    private static final TaskStackBuilderImpl IMPL;

    static {
        if (Build.VERSION.SDK_INT >= 11) {
            IMPL = new TaskStackBuilderImplHoneycomb();
        } else {
            IMPL = new TaskStackBuilderImplBase();
        }
    }

    private final ArrayList<Intent> mIntents = new ArrayList<Intent>();
    private final Context mSourceContext;

    private TaskStackBuilder(Context a) {
        mSourceContext = a;
    }

    /**
     * Return a new TaskStackBuilder for launching a fresh task stack consisting
     * of a series of activities.
     *
     * @param context The context that will launch the new task stack or generate a PendingIntent
     * @return A new TaskStackBuilder
     */
    public static TaskStackBuilder create(Context context) {
        return new TaskStackBuilder(context);
    }

    /**
     * Return a new TaskStackBuilder for launching a fresh task stack consisting
     * of a series of activities.
     *
     * @param context The context that will launch the new task stack or generate a PendingIntent
     * @return A new TaskStackBuilder
     *
     * @deprecated use {@link #create(Context)} instead
     */
    public static TaskStackBuilder from(Context context) {
        return create(context);
    }

    /**
     * Add a new Intent to the task stack. The most recently added Intent will invoke
     * the Activity at the top of the final task stack.
     *
     * @param nextIntent Intent for the next Activity in the synthesized task stack
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addNextIntent(Intent nextIntent) {
        mIntents.add(nextIntent);
        return this;
    }

    /**
     * Add a new Intent with the resolved chain of parents for the target activity to
     * the task stack.
     *
     * <p>This is equivalent to calling {@link #addParentStack(ComponentName) addParentStack}
     * with the resolved ComponentName of nextIntent (if it can be resolved), followed by
     * {@link #addNextIntent(Intent) addNextIntent} with nextIntent.</p>
     *
     * @param nextIntent Intent for the topmost Activity in the synthesized task stack.
     *                   Its chain of parents as specified in the manifest will be added.
     * @return This TaskStackBuilder for method chaining.
     */
    public TaskStackBuilder addNextIntentWithParentStack(Intent nextIntent) {
        ComponentName target = nextIntent.getComponent();
        if (target == null) {
            target = nextIntent.resolveActivity(mSourceContext.getPackageManager());
        }
        if (target != null) {
            addParentStack(target);
        }
        addNextIntent(nextIntent);
        return this;
    }

    /**
     * Add the activity parent chain as specified by manifest &lt;meta-data&gt; elements
     * to the task stack builder.
     *
     * @param sourceActivity All parents of this activity will be added
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addParentStack(Activity sourceActivity) {
        Intent parent = null;
        if (sourceActivity instanceof SupportParentable) {
            parent = ((SupportParentable) sourceActivity).getSupportParentActivityIntent();
        }
        if (parent == null) {
            parent = NavUtils.getParentActivityIntent(sourceActivity);
        }

        if (parent != null) {
            // We have the actual parent intent, build the rest from static metadata
            // then add the direct parent intent to the end.
            ComponentName target = parent.getComponent();
            if (target == null) {
                target = parent.resolveActivity(mSourceContext.getPackageManager());
            }
            addParentStack(target);
            addNextIntent(parent);
        }
        return this;
    }

    /**
     * Add the activity parent chain as specified by manifest &lt;meta-data&gt; elements
     * to the task stack builder.
     *
     * @param sourceActivityClass All parents of this activity will be added
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addParentStack(Class<?> sourceActivityClass) {
        return addParentStack(new ComponentName(mSourceContext, sourceActivityClass));
    }

    /**
     * Add the activity parent chain as specified by manifest &lt;meta-data&gt; elements
     * to the task stack builder.
     *
     * @param sourceActivityName Must specify an Activity component. All parents of
     *                           this activity will be added
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addParentStack(ComponentName sourceActivityName) {
        final int insertAt = mIntents.size();
        try {
            Intent parent = NavUtils.getParentActivityIntent(mSourceContext, sourceActivityName);
            while (parent != null) {
                mIntents.add(insertAt, parent);
                parent = NavUtils.getParentActivityIntent(mSourceContext, parent.getComponent());
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Bad ComponentName while traversing activity parent metadata");
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    /**
     * @return the number of intents added so far.
     */
    public int getIntentCount() {
        return mIntents.size();
    }

    /**
     * Get the intent at the specified index.
     * Useful if you need to modify the flags or extras of an intent that was previously added,
     * for example with {@link #addParentStack(Activity)}.
     *
     * @param index Index from 0-getIntentCount()
     * @return the intent at position index
     *
     * @deprecated Renamed to editIntentAt to better reflect intended usage
     */
    public Intent getIntent(int index) {
        return editIntentAt(index);
    }

    /**
     * Return the intent at the specified index for modification.
     * Useful if you need to modify the flags or extras of an intent that was previously added,
     * for example with {@link #addParentStack(Activity)}.
     *
     * @param index Index from 0-getIntentCount()
     * @return the intent at position index
     */
    public Intent editIntentAt(int index) {
        return mIntents.get(index);
    }

    /**
     * @deprecated Use editIntentAt instead
     */
    public Iterator<Intent> iterator() {
        return mIntents.iterator();
    }

    /**
     * Start the task stack constructed by this builder. The Context used to obtain
     * this builder must be an Activity.
     *
     * <p>On devices that do not support API level 11 or higher the topmost activity
     * will be started as a new task. On devices that do support API level 11 or higher
     * the new task stack will be created in its entirety.</p>
     */
    public void startActivities() {
        startActivities(null);
    }

    /**
     * Start the task stack constructed by this builder. The Context used to obtain
     * this builder must be an Activity.
     *
     * <p>On devices that do not support API level 11 or higher the topmost activity
     * will be started as a new task. On devices that do support API level 11 or higher
     * the new task stack will be created in its entirety.</p>
     *
     * @param options Additional options for how the Activity should be started.
     * See {@link Context#startActivity(Intent, Bundle)
     */
    public void startActivities(Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException(
                    "No intents added to TaskStackBuilder; cannot startActivities");
        }

        Intent[] intents = mIntents.toArray(new Intent[mIntents.size()]);
        intents[0] = new Intent(intents[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
                IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        if (!ContextCompat.startActivities(mSourceContext, intents, options)) {
            Intent topIntent = new Intent(intents[intents.length - 1]);
            topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mSourceContext.startActivity(topIntent);
        }
    }

    /**
     * Obtain a {@link PendingIntent} for launching the task constructed by this builder so far.
     *
     * @param requestCode Private request code for the sender
     * @param flags May be {@link PendingIntent#FLAG_ONE_SHOT},
     *              {@link PendingIntent#FLAG_NO_CREATE}, {@link PendingIntent#FLAG_CANCEL_CURRENT},
     *              {@link PendingIntent#FLAG_UPDATE_CURRENT}, or any of the flags supported by
     *              {@link Intent#fillIn(Intent, int)} to control which unspecified parts of the
     *              intent that can be supplied when the actual send happens.
     * @return The obtained PendingIntent
     */
    public PendingIntent getPendingIntent(int requestCode, int flags) {
        return getPendingIntent(requestCode, flags, null);
    }

    /**
     * Obtain a {@link PendingIntent} for launching the task constructed by this builder so far.
     *
     * @param requestCode Private request code for the sender
     * @param flags May be {@link PendingIntent#FLAG_ONE_SHOT},
     *              {@link PendingIntent#FLAG_NO_CREATE}, {@link PendingIntent#FLAG_CANCEL_CURRENT},
     *              {@link PendingIntent#FLAG_UPDATE_CURRENT}, or any of the flags supported by
     *              {@link Intent#fillIn(Intent, int)} to control which unspecified parts of the
     *              intent that can be supplied when the actual send happens.
     * @param options Additional options for how the Activity should be started.
     * See {@link Context#startActivity(Intent, Bundle)
     * @return The obtained PendingIntent
     */
    public PendingIntent getPendingIntent(int requestCode, int flags, Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException(
                    "No intents added to TaskStackBuilder; cannot getPendingIntent");
        }

        Intent[] intents = mIntents.toArray(new Intent[mIntents.size()]);
        intents[0] = new Intent(intents[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
                IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        // Appropriate flags will be added by the call below.
        return IMPL.getPendingIntent(mSourceContext, intents, requestCode, flags, options);
    }

    /**
     * Return an array containing the intents added to this builder. The intent at the
     * root of the task stack will appear as the first item in the array and the
     * intent at the top of the stack will appear as the last item.
     *
     * @return An array containing the intents added to this builder.
     */
    public Intent[] getIntents() {
        Intent[] intents = new Intent[mIntents.size()];
        if (intents.length == 0) return intents;

        intents[0] = new Intent(mIntents.get(0)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                IntentCompat.FLAG_ACTIVITY_CLEAR_TASK |
                IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        for (int i = 1; i < intents.length; i++) {
            intents[i] = new Intent(mIntents.get(i));
        }
        return intents;
    }
}
