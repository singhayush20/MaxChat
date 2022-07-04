package com.example.maxchat;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String FRIENDLY_MSG_LENGTH_KEY = "default_message_length";
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    //Create a ChildEventListener variable.
    private ChildEventListener mChildEventListener;

    //Instantiate FirebaseAuth
    private FirebaseAuth mFirebaseAuth;
    //Instantiate AuthStateListener
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    //RC stands for Request code
    //Its a flag for when we return from starting the
    //Activity for the Result in onAuthStateChanged()
    public static final String RC_SIGN_IN = "1";
    private static final String REQUEST_CODE = "REQUEST_CODE";

    //We need a constant for startActivityForResult, define RC_PHOTO_PICKER
    private static final String RC_PHOTO_PICKER = "2";

    //FirebaseStorage
    private FirebaseStorage mFirebaseStorage;
    //StorageReferenceObject
    private StorageReference mStorageReference;
    //Firebase remote config instance
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    //This ActivityResultLauncher will be required to show the sign-in screen
    ActivityResultLauncher<Intent> signInLauncher
            = registerForActivityResult(new FirebaseAuthUIActivityResultContract(), (result) -> {
//     Handle the FirebaseAuthUIAuthenticationResult
//     ...
        if (result.getResultCode() == RESULT_OK) {
            Toast.makeText(this, "Signed in!", Toast.LENGTH_LONG).show();

        } else if (result.getResultCode() == RESULT_CANCELED) {
            Toast.makeText(this, "Sign in cancelled!", Toast.LENGTH_LONG).show();
            finish();
        }
    });
    //    });
    ActivityResultLauncher<Intent> photoChooserResultLauncher
            = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    //There are no request codes
                    Intent data = result.getData();
                    Uri selectedImageUri = data.getData();
                    Log.i(TAG + " ###", "Image Uri: " + selectedImageUri.toString());
                /*
                We take the reference to chat_photos and make a child which will
                be named after the last part of path segment of the Uri
                Ex- content://local_images/foo/4 then the image will be saved with
                named as 4
                 */
                    //StorageReference photoRef = mStorageReference.child(selectedImageUri.getLastPathSegment());
                    //Log.i(TAG+" ###","last path segment: "+selectedImageUri.getLastPathSegment());

//                    photoRef.putFile(selectedImageUri);//upload the photo
//                    Task<Uri> url=photoRef.getDownloadUrl();
//                    Log.i(TAG+" ###","download Url: "+url);
//                    photoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
//                        @Override
//                        public void onSuccess(Uri uri) {
//                            Log.i(TAG + " ###", "Image uploaded successfully, downloadUrl: " + uri.toString());
//                            //We save the URL to the database
//                            FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, uri.toString());
//                            mDatabaseReference.push().setValue(friendlyMessage);
//                        }
//
//                    }).addOnFailureListener(new OnFailureListener() {
//                        @Override
//                        public void onFailure(@NonNull Exception e) {
//                            Log.e(TAG+" ###","onSuccess() not called, onFailure called: "+e);
//                        }
//                    });
                    StorageReference fileRef = mStorageReference.child(System.currentTimeMillis() + "." + getFileExtension(selectedImageUri));
                    fileRef.putFile(selectedImageUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            fileRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    //mAKE THE PROGRESS BAR INVISIBLE
                                    Toast.makeText(MainActivity.this, "Uploaded successfully", Toast.LENGTH_LONG).show();
                                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, uri.toString());
                                    mDatabaseReference.push().setValue(friendlyMessage);
                                }
                            });
                        }
                    }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                            //Set progress bar visibility to true
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //Make progress bar invisible
                            Toast.makeText(MainActivity.this, "Upload failed!", Toast.LENGTH_LONG).show();
                        }
                    });

                }
            }
    );

    private String getFileExtension(Uri uri) {
        ContentResolver cr = getContentResolver();//cannot be referenced from a static reference
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cr.getType(uri));

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG + " ###", "entered onCreate() method");
        setContentView(R.layout.activity_main);
        mUsername = ANONYMOUS;
        mFirebaseDatabase = FirebaseDatabase.getInstance(); //Entry point for our database

        //Initialize the FirebaseAuth object
        mFirebaseAuth = FirebaseAuth.getInstance();
        //.getReference() gives a root node reference
        //.child("messages")- specifically specifying the messages of the database
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");

        //Initialize FirebaseStorage
        mFirebaseStorage = FirebaseStorage.getInstance();
        mStorageReference = mFirebaseStorage.getReference().child("chat_photos/");

        //Initialize FirebaseConfig instance
        mFirebaseRemoteConfig=FirebaseRemoteConfig.getInstance();
        //Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        //Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        //Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Fire an intent to show an image picker
            }
        });
        //Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                Log.i(TAG + " ###", "entered the onTextChanged method");
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)
        });

        //Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG + " ###", "SEND button clicked, onClick() called from onCreate()");
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                /*
                This object has all the keys that we’ll
                store as a message in the realtime database.
                In the next step we’ll store this data to the cloud
                in our realtime database.
                 */
                mDatabaseReference.push().setValue(friendlyMessage);
                Log.i(TAG + " ###", "push() called on mDatabaseReference");
                mMessageEditText.setText("");
            }
        });

//        mChildEventListener = new ChildEventListener() {
//            //This method gets called every time a new message is
//            //inserted into the messages list.
//            //This is also triggered for every message in the list
//            //the first time it is attached.
//            @Override
//            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                Log.i(TAG + " ###", "onChildAdded() called: ");
//               /*
//               Data snapshot contains the data from the firebase, at
//               a specific location, at the exact time the listener is triggered
//                In this case, it contains the new message which is added.
//                */
//                FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
//                //The value gets deserialized to a FriendlyMessage object.
//                //Now add the new message to the adapter.
//                mMessageAdapter.add(friendlyMessage);
//            }
//
//            //Called when the contents of an existing message gets changed.
//            @Override
//            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                Log.i(TAG + " ###", "onChildChanged() called");
//            }
//
//            //Called when an existing message is deleted
//            @Override
//            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
//                Log.i(TAG + " ###", "onChildRemoved() called");
//            }
//
//            //Called when a message changes position in the list.
//            @Override
//            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                Log.i(TAG + " ###", "onChildMoved() called");
//            }
//
//            //Called- indicates that some sort of error occurred
//            //while trying to make some changes
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.i(TAG + " ###", "onCancelled() called");
//            }
//        };
//        //add the EventListener
//        //The reference defined what we are listening to
//        //Listener determines what exactly will happen to the data
//        mDatabaseReference.addChildEventListener(mChildEventListener);


        Log.i(TAG + " ###", "initializing the mAuthStateListener variable");
        //Initialize the AuthStateListener
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                Log.i(TAG + " ###", "entered the onAuthStateChanged() method");
        /*
            We want to check if the user is logged in or not.
            We will show the login screen if the user is not locked in.
        */
                //The firebaseAuth parameter passed in the onAuthStateChanged() is
                //guaranteed to have the state whether the user is logged in or not
                //It is not the same object which we created.
                FirebaseUser user = firebaseAuth.getCurrentUser();
                //Log.i(TAG+" ###","user: "+user.getEmail());
                if (user != null) {
                    Log.i(TAG + " ###", "user is logged in!");
                    //user is signed in successfully
                    Toast.makeText(MainActivity.this, "You are signed in successfully!", Toast.LENGTH_LONG).show();

                    //When signed in display the message list and user name
                    //Pass the user name for the user
                    Log.i(TAG + " ###", "calling onSignedInitialize for: " + user.getDisplayName());
                    onSignedInitialize(user.getDisplayName());
                } else {
                    //On signing out-
                    onSignedOutCleanUp();
                    Log.i(TAG + " ###", "user is not logged in!");
                    //user is signed out
                    //Enable sign-in providers

                    /*
                        SmartLock allows phone to save user credentials and
                        automatically log them in
                    */
                    //https://github.com/firebase/FirebaseUI-Android/blob/master/auth/README.md#themes

                    Log.i(TAG + " ###", "creating signInIntent:");
                    Intent signInIntent = AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false).setTheme(R.style.LogInscreen)
                            .setAvailableProviders
                                    (Arrays.asList(new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build())).build();
                    //signInIntent.putExtra(REQUEST_CODE, RC_SIGN_IN);
                    Log.i(TAG + " ###", "launching intent from else in onAuthChanged()");
                    signInLauncher.launch(signInIntent);

                }
            }
        };
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG + " ###", "setting onClickListener for PhotoPicker button in onClick()");
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                //intent.putExtra(REQUEST_CODE, RC_PHOTO_PICKER);
                //Log.i(TAG + " ###", "REQUEST CODE: " + intent.getStringExtra(REQUEST_CODE));
                photoChooserResultLauncher.launch(intent);//Intent.createChooser(intent, "Complete action using"));
                Log.i(TAG + " ###", "PhotoChooser activity started");
            }
        });
        //Enable developer mode
//        FirebaseRemoteConfigSettings configSettings=new FirebaseRemoteConfigSettings.Builder()
//                .setDeveloperModeEnabled(BuildConfig.DEBUG).build();
        //The method setDeveloperModeEnabled() is deprecated
        FirebaseRemoteConfigSettings configSettings=new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0).build();//for production builds set it to 1 hour
        //for development purpose set it to small value.
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
        Log.i(TAG+" ###","ConfigSettings for remoteConfigSettings is set");
        Map<String,Object> defaultConfigMap=new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY,DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaultsAsync(defaultConfigMap);
        fetchConfig();
        Log.i(TAG + " ###", "returning from onCreate()");

    }
    public void fetchConfig()
    {
        long cacheExpiration=0; //for production builds
        //for development mode set it to 0
        Log.i(TAG+" ###","entered the fetchConfig() method, cacheExpirationTime="+cacheExpiration);
        //if(mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled())
          //  cacheExpiration=0;//0 for developer mode enabled
        //This allows to fetch the changes from the firebase immediately
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.i(TAG+" ###","entered the onSuccess() on fetching from remote config");
                        //activate the fetched values
                            mFirebaseRemoteConfig.activate();
                            applyRetrievedLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG+"###"," Some problem occurred: onSuccess \nnot called for fetching from remote config: "+e);
                        //call applyRetrievedLengthLimit();
                        /*
                        If we are offline, onSuccess() will not be called
                        Also, the method applyRetrievedLengthLimit() will ensure that
                        the value in cache is up to date.
                         */
                        applyRetrievedLengthLimit();

                    }
                });
    }
    private void applyRetrievedLengthLimit()
    {
        Log.i(TAG+" ###","entered the applyRetrievedLengthLimit method");
        //Appropriately update the message length
        long friendly_message_length=mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        Log.i(TAG+" ###","new friendly message length: "+friendly_message_length);
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter((int)friendly_message_length)
        });
    }


    //This method is called before onResume() therefore
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        Log.i(TAG + " ###", "entered the onActivityResult() method" +
//                "\n requestCode=" + requestCode + " resultCode: " + resultCode);
//        super.onActivityResult(requestCode, resultCode, data);
//
//        assert data != null;
//        String requestCodeExtra = data.getStringExtra(REQUEST_CODE);
//        Log.i(TAG + " ###", "requestCodeExtra: " + requestCodeExtra);
//        if (requestCodeExtra == RC_SIGN_IN)//if the activity that's being returned from was
//        //login flow
//        {
//            if (resultCode == RESULT_OK) {
//                Toast.makeText(this, "Signed in!", Toast.LENGTH_LONG).show();
//                finish();
//            } else if (resultCode == RESULT_CANCELED) {
//                Toast.makeText(this, "Sign in cancelled!", Toast.LENGTH_LONG).show();
//            }
//        }
//        else if (requestCodeExtra == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
//            Log.i(TAG + " ###", "requestCode extra is: " + requestCodeExtra + ", photo chooser");
//            Uri selectedImageUri = data.getData();
//                /*
//                We take the reference to chat_photos and make a child which will
//                be named after the last part of path segment of the Uri
//                Ex- content://local_images/foo/4 then the image will be saved with
//                named as 4
//                 */
//            StorageReference photoRef = mStorageReference.child(selectedImageUri.getLastPathSegment());
//            photoRef.putFile(selectedImageUri);
//            photoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
//                @Override
//                public void onSuccess(Uri uri) {
//                    Log.i(TAG + " ###", "Image uploaded successfully, downloadUrl: " + uri.toString());
//                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, uri.toString());
//                    mDatabaseReference.push().setValue(friendlyMessage);
//                }
//            });
////        Upload file to Firebase Storage
////        photoRef.putFile(selectedImageUri);
////        putFile returns an UploadTask, we will add an
////        OnCompletionListener to it which takes the first
////        argument as an activity
////                photoRef.putFile(selectedImageUri).addOnSuccessListener(
////                        this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
////
////                            @Override
////                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
////                                //onSuccess has a TaskSnapshot parameter, which will be our
////                                //key to getting the URL of the file that was just sent to storage
////                                Uri downloadUrl=taskSnapshot.getDownloadUrl();
////                                FriendlyMessage friendlyMessage=new FriendlyMessage(null,mUsername,downloadUrl.toString());
////                                mDatabaseReference.push().setValue(friendlyMessage);
////                            }
////                        }
////                );
//    }
//
//    }

    /**
     * {@inheritDoc}
     * <p>
     * Dispatch onResume() to fragments.  Note that for better inter-operation
     * with older versions of the platform, at the point of this call the
     * fragments attached to the activity are <em>not</em> resumed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG + " ###", "entered the onResume() method" +
                "\nadding AuthStateListener to mFirebaseAuth");
        //Add the AuthStateListener to the FirebaseAuth
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Dispatch onPause() to fragments.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG + " ###", "entered the onPause() method()");
        //Remove the AuthStateListener
        if (mAuthStateListener != null)
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        /*
        Following two lines of code will make sure
        that when the activity is destroyed in a way that
        has nothing to do with signing out, such as an
        app rotation; the listener is effectively cleaned up.
         */
        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                Log.i(TAG + " ###", "signing out");
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSignedInitialize(String username) {
        Log.i(TAG + " ###", "entered onSignedInitialize(): ");
        /*
            In the onClick() method, now the Username will be set
            Any message that is sent will have that username.
          FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);

         */
        mUsername = username;
        //This code is added here so that we can load the messages once
        //the user has logged in
        attachDatabaseReadListener();

    }

    private void onSignedOutCleanUp() {
        /*
        Here we tear down the UI,
        unset the Username, clear the messages list and
        detach the listener.
         */
        //Unset the username
        mUsername = ANONYMOUS;
        //Clear the messages from the adapter
        //We do this so that a user who is no longer signed in
        //should not be able to see these messages
        /*
        If we don't do this, we will get duplicate messages when we
        log in and out multiple times.
         */
        mMessageAdapter.clear();

        detachDatabaseReadListener();

    }

    private void attachDatabaseReadListener() {
        Log.i(TAG + " ###", "entered attachDatabaseReadListener() method");
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                //This method gets called every time a new message is
                //inserted into the messages list.
                //This is also triggered for every message in the list
                //the first time it is attached.
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    Log.i(TAG + " ###", "onChildAdded() called: ");
               /*
               Data snapshot contains the data from the firebase, at
               a specific location, at the exact time the listener is triggered
                In this case, it contains the new message which is added.
                */
                    FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
                    //The value gets deserialized to a FriendlyMessage object.
                    //Now add the new message to the adapter.
                    mMessageAdapter.add(friendlyMessage);
                }

                //Called when the contents of an existing message gets changed.
                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    Log.i(TAG + " ###", "onChildChanged() called");
                }

                //Called when an existing message is deleted
                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    Log.i(TAG + " ###", "onChildRemoved() called");
                }

                //Called when a message changes position in the list.
                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    Log.i(TAG + " ###", "onChildMoved() called");
                }

                //Called- indicates that some sort of error occurred
                //while trying to make some changes
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.i(TAG + " ###", "onCancelled() called");
                }
            };
            //add the EventListener
            //The reference defined what we are listening to
            //Listener determines what exactly will happen to the data
            mDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener() {
        //Make sure that the childEventListener is not null
        if (mChildEventListener != null) {
            //Remove the ReadListener
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }

    }
}