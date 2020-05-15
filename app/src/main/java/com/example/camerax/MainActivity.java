package com.example.camerax;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CameraX 可以做的事情，总体来说分三个部分–图像预览PreView，图像分析ImageAnalysis，图像拍摄ImageCapture。
 * 我们要分别对它们进行配置，告诉 CameraX 我们要怎么用它们。
 * ImageCapture类、ImageAnalysis类、Preview类的对象就是我们具体操作使用的对象
 */
public class MainActivity extends AppCompatActivity {

    private int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"};
    TextureView txView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txView = findViewById(R.id.view_finder);

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        //make sure there isn't another camera instance running before starting
        CameraX.unbindAll();

        /************************************************************/
        int aspRatioW = txView.getWidth(); //get width of screen
        int aspRatioH = txView.getHeight(); //get height
        Rational asp = new Rational(aspRatioW, aspRatioH); //aspect ratio
        Size screen = new Size(aspRatioW, aspRatioH); //size of the screen
        //图片预览Preview
        PreviewConfig pConfig = new PreviewConfig.Builder()
                //设置此配置的图像的预期目标的宽高比
                .setTargetAspectRatio(asp)
                //从该配置设置目标的分辨率
                .setTargetResolution(screen).build();
        Preview preview = new Preview(pConfig);
        //预览用例会生成一个流式传输相机输入的 SurfaceTexture。它还提供对 View 进行剪裁、缩放或旋转以确保其正常显示所需的其他信息。
        //当相机处于活动状态时，图片预览就会流式传输到此 SurfaceTexture。SurfaceTexture 可以连接到 TextureView 或 GLSurfaceView。
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            //to update the surface texture we have to destroy it first, then re-add it
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) txView.getParent();
                parent.removeView(txView);
                parent.addView(txView, 0);

                txView.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });
        /************************************************************/
        //图片拍摄ImageCapture
        ImageCaptureConfig imgCapConfig = new ImageCaptureConfig.Builder()
                //要缩短照片拍摄的延迟时间，将 ImageCapture.CaptureMode 设置为 MIN_LATENCY。要优化照片质量，将其设置为 MAX_QUALITY。
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                //设置图像捕获模式
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        final ImageCapture imageCapture = new ImageCapture(imgCapConfig);
        //给拍照按钮设置点击事件
        findViewById(R.id.capture_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Environment.getExternalStorageDirectory():This method was deprecated in API level 29. To improve user privacy, direct access to shared/external storage devices is deprecated.
                //When an app targets Build.VERSION_CODES.Q, the path returned from this method is no longer directly accessible to apps.
                //Apps can continue to access content stored on shared/external storage by migrating to alternatives such as Context#getExternalFilesDir(String), MediaStore, or Intent#ACTION_OPEN_DOCUMENT.
//                File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".jpg");
                String captureTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                final String fileName = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/" + captureTime + ".jpg";
                File file = new File(fileName);
                imageCapture.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        try {
                            MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), fileName, null);
                            // 发送广播，通知刷新图库的显示
                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + fileName)));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        //给出提示信息
                        Toast.makeText(MainActivity.this, "Photo capture succeeded: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.UseCaseError useCaseError, @NonNull String message, @Nullable Throwable cause) {
                        Toast.makeText(MainActivity.this, "Photo capture failed " + message, Toast.LENGTH_LONG);
                        if (cause != null) {
                            cause.printStackTrace();
                        }
                    }
                });
            }
        });
        /************************************************************/
        //图片分析ImageAnalysis
        //图像分析可以分为两种模式：阻止模式和非阻止模式。
        //阻止模式通过 ImageAnalysis.ImageReaderMode.ACQUIRE_NEXT_IMAGE 设置。在此模式下，分析器会按顺序从相机接收帧；这意味着，如果 analyze 方法花费的时间超过单帧在当前帧速率下的延迟时间，则帧可能不再是最新的帧，因为新帧已被阻止进入流水线，直到该方法返回为止。
        //非阻止模式通过 ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE 设置。在此模式下，分析器会从相机接收调用 analyze 方法时的最后一个可用帧。如果此方法花费的时间超过单帧在当前帧速率下的延迟时间，可能会跳过某些帧，以便在下一次 analyze 接收数据时，它会获取相机流水线中的最后一个可用帧。
        //可以使用 ImageAnalysisConfig.Builder.setCallbackHandler 将 analyze 方法设置为在回调处理程序上运行，这样就可以在分析器功能运行的同时运行流水线的其余部分。如果没有设置处理程序，则 analyze 方法将在主线程上运行。
        ImageAnalysisConfig imgAConfig = new ImageAnalysisConfig.Builder()
                //从该配置设置目标的分辨率
//                .setTargetResolution(new Size(1280, 720))
                //设置图片分析模式：非阻止模式
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .build();
        ImageAnalysis analysis = new ImageAnalysis(imgAConfig);
        analysis.setAnalyzer(
                new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(ImageProxy image, int rotationDegrees) {

                    }
                });
        CameraX.bindToLifecycle((LifecycleOwner) this, analysis, imageCapture, preview);
    }

    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = txView.getMeasuredWidth();
        float h = txView.getMeasuredHeight();

        float centreX = w / 2f; //calc centre of the viewfinder
        float centreY = h / 2f;

        int rotationDgr;
        int rotation = (int) txView.getRotation();

        switch (rotation) { //correct output to account for display rotation
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate(rotationDgr, centreX, centreY);
        txView.setTransform(mx); //apply transformations to TextureView
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

}
