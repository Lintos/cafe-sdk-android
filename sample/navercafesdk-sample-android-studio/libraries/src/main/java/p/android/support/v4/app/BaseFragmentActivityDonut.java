/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.os.Build;
import android.os.Bundle;

/**
 * Base class for {@code FragmentActivity} to be able to use Donut APIs.
 */
public abstract class BaseFragmentActivityDonut extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < 11 && getLayoutInflater().getFactory() == null) {
            // On pre-HC devices we need to manually install ourselves as a Factory.
            // On HC and above, we are automatically installed as a private factory
            getLayoutInflater().setFactory(this);
        }

        super.onCreate(savedInstanceState);
    }

    /**
     * Called when the back button has been pressed.and not handled by the support fragment manager.
     */
    void onBackPressedNotHandled() {
        // on v4, just call finish manually
        finish();
    }

}
