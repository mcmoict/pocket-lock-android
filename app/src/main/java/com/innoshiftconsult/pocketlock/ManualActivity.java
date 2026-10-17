package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Build;

public class ManualActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);
        findViewById(R.id.manualBackButton).setOnClickListener(view -> finish());
        setImageClickListeners(findViewById(android.R.id.content));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ((TextView) findViewById(R.id.manualIntro))
                    .setText("Pocket Lock은 휴대폰이 주머니에 들어간 것을 감지해 화면을 자동으로 잠그는 앱입니다.\n"
                            + "Android 9 이상에서는 자동 잠금 기능을 사용하기 위해 접근성 서비스를 켜야 합니다.\n"
                            + "※ 사용 방법 안내의 그림을 누르면 확대해서 확인할 수 있습니다.");
            ((TextView) findViewById(R.id.manualPermissionHeading))
                    .setText("① 접근성 서비스 켜기");
            ((TextView) findViewById(R.id.manualPermissionDetails))
                    .setText("• 메인 화면에서 [접근성 설정 열기]를 선택합니다.\n"
                            + "• 안내가 표시되면 내용을 확인한 뒤 [동의하고 설정 열기]를 누릅니다.\n"
                            + "• 접근성 설정에서 Pocket Lock을 찾아 서비스를 사용하도록 설정합니다.\n"
                            + "• 앱으로 돌아와 [접근성 서비스 사용 중] 상태인지 확인합니다.\n\n"
                            + "메뉴 이름은 기기에 따라 다를 수 있습니다. Galaxy에서는 [설치된 앱], "
                            + "일부 Android 기기에서는 [다운로드한 앱] 또는 [설치된 서비스]에서 찾을 수 있습니다.\n\n"
                            + "Pocket Lock은 화면 내용이나 입력 정보를 읽거나 수집하지 않으며, 화면 잠금 기능만 실행합니다.");
            findViewById(R.id.accessibilityImageColumn).setVisibility(View.VISIBLE);
            findViewById(R.id.legacyAdminImageRow).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.manualLockOnDetails))
                    .setText("• [주머니 잠금 켜기] 스위치를 켭니다.\n"
                            + "• 아래 문구가 표시되면 정상적으로 작동 중입니다.\n\n"
                            + "'감시중 · 접근성 서비스 사용 중 · 근접 센서로 확인합니다.'\n"
                            + "또는\n"
                            + "'감시중 · 접근성 서비스 사용 중 · 조도·가속도·자세 센서로 확인합니다.'");
            findViewById(R.id.accessibilityLockOnImageRow).setVisibility(View.VISIBLE);
            findViewById(R.id.legacyLockOnImageRow).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.manualUninstallDetails))
                    .setText("① [앱 삭제 준비]를 선택합니다.\n"
                            + "② [앱 정보 열기]를 눌러 앱 정보 화면에서 삭제합니다.\n\n"
                            + "접근성 서비스가 켜져 있어도 앱을 삭제할 수 있습니다. 서비스를 끄려면 "
                            + "[접근성 설정]에서 Pocket Lock을 선택한 뒤 사용을 중지합니다.\n\n"
                            + "이전 Android 버전에서 기기 관리자 권한을 허용한 경우에는 먼저 해당 권한을 해제해야 합니다.");
        } else {
            ((TextView) findViewById(R.id.manualIntro))
                    .setText("Pocket Lock은 휴대폰이 주머니에 들어간 것을 감지해 화면을 자동으로 잠그는 앱입니다.\n\n"
                            + "Android 8.1 이하에서는 화면 잠금 기능에 기기 관리자 권한이 필요합니다. "
                            + "그림을 누르면 확대해서 확인할 수 있습니다.");
            ((TextView) findViewById(R.id.manualPermissionHeading))
                    .setText("① 기기 관리자 권한 허용");
            ((TextView) findViewById(R.id.manualPermissionDetails))
                    .setText("• 메인 화면에서 [기기 관리자 설정 열기]를 선택합니다.\n"
                            + "• Pocket Lock을 선택하고 기기 관리자 권한을 활성화합니다.\n"
                            + "• 앱으로 돌아와 감지 방식을 확인한 뒤 [주머니 잠금 켜기]를 활성화합니다.\n\n"
                            + "※ Android 보안 정책에 따라 잠금 후 지문·얼굴 대신 PIN·패턴·비밀번호를 입력해야 할 수 있습니다.");
            findViewById(R.id.accessibilityImageColumn).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.manualUninstallDetails))
                    .setText("① [앱 삭제 준비]를 선택합니다.\n"
                            + "② 기기 관리자 설정에서 Pocket Lock 권한을 해제합니다.\n"
                            + "③ 설정의 앱 목록에서 Pocket Lock을 삭제합니다.");
        }
    }

    private void setImageClickListeners(View view) {
        if (view instanceof ImageView) {
            view.setOnClickListener(this::showZoomedImage);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setImageClickListeners(group.getChildAt(i));
            }
        }
    }

    private void showZoomedImage(View view) {
        ImageView source = (ImageView) view;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);

        ZoomableImageView image = new ZoomableImageView(this);
        image.setImageDrawable(source.getDrawable());
        content.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView closeButton = new TextView(this);
        closeButton.setText("닫기");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(16);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setPadding(20, 14, 20, 14);
        closeButton.setBackgroundColor(0x99000000);
        closeButton.setOnClickListener(button -> dialog.dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, 24, 16, 0);
        content.addView(closeButton, closeParams);

        dialog.setContentView(content);
        dialog.show();
    }
}
