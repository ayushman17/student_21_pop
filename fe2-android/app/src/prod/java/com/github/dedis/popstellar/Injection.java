package com.github.dedis.popstellar;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.util.Log;

import com.github.dedis.popstellar.model.network.GenericMessage;
import com.github.dedis.popstellar.model.network.answer.Answer;
import com.github.dedis.popstellar.model.network.answer.Result;
import com.github.dedis.popstellar.model.network.answer.ResultMessages;
import com.github.dedis.popstellar.model.network.method.Message;
import com.github.dedis.popstellar.model.network.method.message.MessageGeneral;
import com.github.dedis.popstellar.model.network.method.message.data.Data;
import com.github.dedis.popstellar.model.network.serializer.JsonAnswerSerializer;
import com.github.dedis.popstellar.model.network.serializer.JsonDataSerializer;
import com.github.dedis.popstellar.model.network.serializer.JsonGenericMessageDeserializer;
import com.github.dedis.popstellar.model.network.serializer.JsonMessageGeneralSerializer;
import com.github.dedis.popstellar.model.network.serializer.JsonMessageSerializer;
import com.github.dedis.popstellar.model.network.serializer.JsonResultSerializer;
import com.github.dedis.popstellar.repository.LAORepository;
import com.github.dedis.popstellar.repository.local.LAODatabase;
import com.github.dedis.popstellar.repository.local.LAOLocalDataSource;
import com.github.dedis.popstellar.repository.remote.LAORemoteDataSource;
import com.github.dedis.popstellar.repository.remote.LAORequestFactory;
import com.github.dedis.popstellar.repository.remote.LAOService;
import com.github.dedis.popstellar.utility.scheduler.ProdSchedulerProvider;
import com.github.dedis.popstellar.utility.security.Keys;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;
import com.google.crypto.tink.signature.Ed25519PrivateKeyManager;
import com.google.crypto.tink.signature.PublicKeySignWrapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tinder.scarlet.Scarlet;
import com.tinder.scarlet.lifecycle.android.AndroidLifecycle;
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter;
import com.tinder.scarlet.streamadapter.rxjava2.RxJava2StreamAdapterFactory;
import com.tinder.scarlet.websocket.okhttp.OkHttpClientUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/** Injection is used to provide the services needed to the application. */
public class Injection {

  private static final String SERVER_URL = "ws://10.0.2.2:9000/organizer/client";

  private static final String TAG = "INJECTION";

  private static final String KEYSET_NAME = "POP_KEYSET";

  private static final String SHARED_PREF_FILE_NAME = "POP_KEYSET_SP";

  private static final String MASTER_KEY_URI = "android-keystore://POP_MASTER_KEY";

  private static OkHttpClient OK_HTTP_CLIENT_INSTANCE;

  private static LAORequestFactory REQUEST_FACTORY_INSTANCE;

  private static Scarlet SCARLET_INSTANCE;

  private static LAOService LAO_SERVICE_INSTANCE;

  private static AndroidKeysetManager KEYSET_MANAGER;

  private static ViewModelFactory viewModelFactory;

  public static AndroidKeysetManager provideAndroidKeysetManager(Context applicationContext)
      throws IOException, GeneralSecurityException {

    if (KEYSET_MANAGER == null) {
      SharedPreferences.Editor editor =
          applicationContext
              .getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
              .edit();
      editor.apply();

      Ed25519PrivateKeyManager.registerPair(true);
      PublicKeySignWrapper.register();

      // TODO: move to background thread
      AndroidKeysetManager keysetManager =
          new AndroidKeysetManager.Builder()
              .withSharedPref(applicationContext, KEYSET_NAME, SHARED_PREF_FILE_NAME)
              .withKeyTemplate(Ed25519PrivateKeyManager.rawEd25519Template())
              .withMasterKeyUri(MASTER_KEY_URI)
              .build();

      KeysetHandle publicKeysetHandle = keysetManager.getKeysetHandle().getPublicKeysetHandle();

      try {
        String publicKey = Keys.getEncodedKey(publicKeysetHandle);
        Log.d(TAG, "public key = " + publicKey);
      } catch (IOException e) {
        Log.e(TAG, "failed to retrieve public key", e);
      }

      KEYSET_MANAGER = keysetManager;
    }
    return KEYSET_MANAGER;
  }

  public static Gson provideGson() {
    return new GsonBuilder()
        .registerTypeAdapter(GenericMessage.class, new JsonGenericMessageDeserializer())
        .registerTypeAdapter(Message.class, new JsonMessageSerializer())
        .registerTypeAdapter(Data.class, new JsonDataSerializer())
        .registerTypeAdapter(Result.class, new JsonResultSerializer())
        .registerTypeAdapter(ResultMessages.class, new JsonResultSerializer())
        .registerTypeAdapter(Answer.class, new JsonAnswerSerializer())
        .registerTypeAdapter(MessageGeneral.class, new JsonMessageGeneralSerializer())
        .disableHtmlEscaping()
        .create();
  }

  public static BarcodeDetector provideQRCodeDetector(Context context) {
    return new BarcodeDetector.Builder(context).setBarcodeFormats(Barcode.QR_CODE).build();
  }

  public static CameraSource provideCameraSource(
      Context context, Detector<Barcode> qrDetector, int width, int height) {
    return new CameraSource.Builder(context, qrDetector)
        .setFacing(CameraSource.CAMERA_FACING_BACK)
        .setRequestedPreviewSize(width, height)
        .setRequestedFps(15.0f)
        .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
        .build();
  }

  public static OkHttpClient provideOkHttpClient() {
    if (OK_HTTP_CLIENT_INSTANCE == null) {
      Log.d(TAG, "creating new OkHttpClient");
      OK_HTTP_CLIENT_INSTANCE =
          new OkHttpClient.Builder()
              .addInterceptor(
                  new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
              .build();
    }
    return OK_HTTP_CLIENT_INSTANCE;
  }

  public static Scarlet provideScarlet(
      Application application, OkHttpClient okHttpClient, Gson gson) {
    if (SCARLET_INSTANCE == null) {
      Log.d(TAG, "creating new Scarlet");
      SCARLET_INSTANCE =
          new Scarlet.Builder()
              .webSocketFactory(
                  OkHttpClientUtils.newWebSocketFactory(okHttpClient, provideRequestFactory()))
              .addMessageAdapterFactory(new GsonMessageAdapter.Factory(gson))
              .addStreamAdapterFactory(new RxJava2StreamAdapterFactory())
              .lifecycle(AndroidLifecycle.ofApplicationForeground(application))
              // .backoffStrategy(new ExponentialBackoffStrategy())
              .build();
    }
    return SCARLET_INSTANCE;
  }

  public static LAORequestFactory provideRequestFactory() {
    if (REQUEST_FACTORY_INSTANCE == null) {
      REQUEST_FACTORY_INSTANCE = new LAORequestFactory(SERVER_URL);
    }
    return REQUEST_FACTORY_INSTANCE;
  }

  public static LAOService provideLAOService(Scarlet scarlet) {
    if (LAO_SERVICE_INSTANCE == null) {
      LAO_SERVICE_INSTANCE = scarlet.create(LAOService.class);
    }
    return LAO_SERVICE_INSTANCE;
  }

  public static LAORepository provideLAORepository(
      Application application, LAOService service, AndroidKeysetManager keysetManager, Gson gson) {
    LAODatabase db = LAODatabase.getDatabase(application);
    return LAORepository.getInstance(
        LAORemoteDataSource.getInstance(service),
        LAOLocalDataSource.getInstance(db),
        keysetManager,
        gson,
        new ProdSchedulerProvider());
  }

  public static synchronized ViewModelFactory provideViewModelFactory(Application application) {
    if (viewModelFactory == null) {
      Log.d(
          ViewModelFactory.class.getSimpleName(),
          "Creating new instance of " + ViewModelFactory.class.getSimpleName());
      viewModelFactory = new ViewModelFactory(application);
    }
    return viewModelFactory;
  }
}
