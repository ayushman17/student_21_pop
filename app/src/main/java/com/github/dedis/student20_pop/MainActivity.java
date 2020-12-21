package com.github.dedis.student20_pop;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.github.dedis.student20_pop.model.Lao;
import com.github.dedis.student20_pop.model.Person;
import com.github.dedis.student20_pop.ui.CameraPermissionFragment;
import com.github.dedis.student20_pop.ui.ConnectingFragment;
import com.github.dedis.student20_pop.ui.HomeFragment;
import com.github.dedis.student20_pop.ui.LaunchFragment;
import com.github.dedis.student20_pop.ui.QRCodeScanningFragment;
import com.github.dedis.student20_pop.ui.QRCodeScanningFragment.QRCodeScanningType;
import com.github.dedis.student20_pop.utility.qrcode.OnCameraAllowedListener;
import com.github.dedis.student20_pop.utility.qrcode.OnCameraNotAllowedListener;
import com.github.dedis.student20_pop.utility.qrcode.QRCodeListener;
import com.github.dedis.student20_pop.utility.security.PrivateInfoStorage;

import java.util.Collections;
import java.util.Date;

import static com.github.dedis.student20_pop.ui.QRCodeScanningFragment.QRCodeScanningType.CONNECT_LAO;

/**
 * Activity used to display the different UIs
 **/
public final class MainActivity extends FragmentActivity implements OnCameraNotAllowedListener, QRCodeListener, OnCameraAllowedListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        if (findViewById(R.id.fragment_container_main) != null) {
            if (savedInstanceState != null) {
                return;
            }

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container_main, new HomeFragment()).commit();
        }
    }

    /**
     * Manage the fragment change after clicking a specific view.
     *
     * @param view the clicked view
     */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tab_home:
                showFragment(new HomeFragment(), HomeFragment.TAG);
                break;
            case R.id.tab_connect:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    showFragment(new QRCodeScanningFragment(CONNECT_LAO), QRCodeScanningFragment.TAG);
                else
                    showFragment(new CameraPermissionFragment(CONNECT_LAO), CameraPermissionFragment.TAG);
                break;
            case R.id.tab_launch:
                showFragment(new LaunchFragment(), LaunchFragment.TAG);
                break;
            case R.id.button_launch:
                String name = ((EditText) findViewById(R.id.entry_box_launch)).getText().toString();
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.exception_message_empty_lao_name), Toast.LENGTH_SHORT).show();
                } else {
                    final PoPApplication app = ((PoPApplication) getApplication());
                    // Creating the LAO and adding it to the organizer's LAO
                    Lao lao = new Lao(name, new Date(), app.getPerson().getId());
                    // Store the private key of the organizer
                    //TODO Move it into app onCreate()
                    if (PrivateInfoStorage.storeData(this, app.getPerson().getId(), app.getPerson().getAuthentication()))
                        Log.d(TAG, "Stored private key of organizer");

                    app.getLocalProxy()
                            .thenCompose(p -> p.createLao(lao.getName(), lao.getTime(), lao.getTime(), app.getPerson().getId()))
                            .thenAccept(code -> {
                                Person organizer = app.getPerson().setLaos(Collections.singletonList(lao.getId()));
                                // Set LAO and organizer information locally
                                ((PoPApplication) getApplication()).setPerson(organizer);
                                ((PoPApplication) getApplication()).addLao(lao);
                                ((PoPApplication) getApplication()).setCurrentLao(lao);
                                // Start the Organizer Activity (user is considered an organizer)
                                Intent intent = new Intent(this, OrganizerActivity.class);
                                startActivity(intent);
                            })
                            .exceptionally(t -> {
                                Toast toast = Toast.makeText(this, "An error occurred : \n" + t.getMessage(), Toast.LENGTH_LONG);
                                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                                toast.show();

                                Log.e(TAG, "Error while creating Lao", t);
                                return null;
                            });
                }
                break;
            case R.id.button_cancel_launch:
                ((EditText) findViewById(R.id.entry_box_launch)).getText().clear();
                showFragment(new HomeFragment(), LaunchFragment.TAG);
                break;
            default:
        }
    }

    private void showFragment(Fragment fragment, String TAG) {
        if (!fragment.isVisible()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container_main, fragment, TAG)
                    .addToBackStack(TAG)
                    .commit();
        }
    }


    @Override
    public void onCameraNotAllowedListener(QRCodeScanningType qrCodeScanningType) {
        showFragment(new CameraPermissionFragment(qrCodeScanningType), CameraPermissionFragment.TAG);
    }

    @Override
    public void onQRCodeDetected(String url, QRCodeScanningType qrCodeScanningType) {
        Log.i(TAG, "Received qrcode url : " + url);
        switch (qrCodeScanningType) {
            case ADD_ROLL_CALL:
                //TODO
                break;
            case ADD_WITNESS:
                //TODO
                break;
            case CONNECT_LAO:
                showFragment(ConnectingFragment.newInstance(url), ConnectingFragment.TAG);
                break;
            default:
                break;
        }
    }

    @Override
    public void onCameraAllowedListener(QRCodeScanningType qrCodeScanningType) {
        showFragment(new QRCodeScanningFragment(qrCodeScanningType), QRCodeScanningFragment.TAG);
    }
}