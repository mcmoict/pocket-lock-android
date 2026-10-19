package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ManualActivity extends Activity {
    static final String EXTRA_GUIDE_ACTION = "com.innoshiftconsult.pocketlock.GUIDE_ACTION";
    static final String ACTION_PERMISSION = "permission";
    static final String ACTION_DETECTION = "detection";
    static final String ACTION_DIAGNOSTICS = "diagnostics";
    static final String ACTION_UNINSTALL = "uninstall";

    private static final String POCKET_INSERTION_VIDEO_URL =
            "https://youtube.com/shorts/CXO_dQ71c10?feature=share";
    private static final int[][] MAIN_ACCORDION_IDS = {
            {R.id.helpLockHeader, R.id.helpLockPanel},
            {R.id.helpSensorsHeader, R.id.helpSensorsPanel},
            {R.id.helpUninstallHeader, R.id.helpUninstallPanel}
    };
    private static final String[] MAIN_ACCORDION_LABELS = {
            "화면이 잠기지 않아요",
            "센서 방식이 궁금해요",
            "앱을 삭제하고 싶어요"
    };
    private static final int[][] DETAIL_ACCORDION_IDS = {
            {R.id.helpDiagnosticsDetailsHeader, R.id.helpDiagnosticsDetailsPanel},
            {R.id.helpPermissionDetailsHeader, R.id.helpPermissionDetailsPanel},
            {R.id.helpSensorTechnicalHeader, R.id.helpSensorTechnicalPanel}
    };
    private static final String[] DETAIL_ACCORDION_LABELS = {
            "센서 진단 결과 보내기",
            "화면 잠금 권한과 안내 자세히 보기",
            "센서 동작 자세히 보기"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);
        findViewById(R.id.manualBackButton).setOnClickListener(view -> finish());
        setImageClickListeners(findViewById(android.R.id.content));
        findViewById(R.id.pocketInsertionVideoLink)
                .setOnClickListener(view -> openPocketInsertionVideo());

        bindAccordionGroup(MAIN_ACCORDION_IDS, MAIN_ACCORDION_LABELS,
                savedInstanceState, "manual_main_accordion", true);
        bindAccordionGroup(DETAIL_ACCORDION_IDS, DETAIL_ACCORDION_LABELS,
                savedInstanceState, "manual_detail_accordion", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ((TextView) findViewById(R.id.manualPermissionSummary))
                    .setText("접근성 설정에서 Pocket Lock 사용을 켜주세요.");
            ((ImageView) findViewById(R.id.manualPermissionImage1))
                    .setImageResource(R.drawable.manual_accessibility_02);
            ((ImageView) findViewById(R.id.manualPermissionImage2))
                    .setImageResource(R.drawable.manual_accessibility_03);
            ((TextView) findViewById(R.id.manualUninstallDetails))
                    .setText("앱 정보 화면에서 삭제할 수 있습니다.\n\n"
                            + "접근성 서비스가 켜져 있어도 일반적인 앱 삭제 절차를 진행할 수 있습니다. "
                            + "서비스를 끄려면 접근성 설정에서 Pocket Lock을 찾아 사용을 중지할 수 있습니다. "
                            + "이전 Android 버전에서 기기 관리자 권한을 허용한 경우에는 해당 권한 해제가 필요할 수 있습니다.");
            findViewById(R.id.manualPermissionDetailAdminImage).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.manualLockStatusDetails))
                    .setText("주머니 잠금이 켜지면 '감시중 · 접근성 서비스 사용 중 · 근접 센서로 확인합니다.' "
                            + "또는 '감시중 · 접근성 서비스 사용 중 · 조도·가속도·자세 센서로 확인합니다.'가 표시됩니다.");
        } else {
            ((TextView) findViewById(R.id.manualPermissionSummary))
                    .setText("기기 관리자 권한을 허용해 주세요.");
            ((ImageView) findViewById(R.id.manualPermissionImage1))
                    .setImageResource(R.drawable.manual_admin);
            ((ImageView) findViewById(R.id.manualPermissionImage1))
                    .setContentDescription("기기 관리자 권한 활성화 화면. 탭하여 확대할 수 있습니다.");
            findViewById(R.id.manualPermissionImage2).setVisibility(View.GONE);
            findViewById(R.id.manualPermissionCaption2).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.manualPermissionCaption1))
                    .setText("기기 관리자 권한 활성화");
            ((TextView) findViewById(R.id.manualUninstallDetails))
                    .setText("기기 관리자 권한이 활성화되어 있다면 먼저 기기 관리자 권한을 해제한 뒤, "
                            + "휴대폰 설정에서 앱을 삭제해 주세요.");
            ((TextView) findViewById(R.id.manualLockStatusDetails))
                    .setText("주머니 잠금이 켜지면 '감시중 · 근접 센서로 주머니 상태를 확인합니다.' "
                            + "또는 '감시중 · 조도·가속도·자세 센서로 주머니 상태를 확인합니다.'가 표시됩니다.");
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveAccordionStates(outState, MAIN_ACCORDION_IDS, "manual_main_accordion");
        saveAccordionStates(outState, DETAIL_ACCORDION_IDS, "manual_detail_accordion");
        super.onSaveInstanceState(outState);
    }

    private void saveAccordionStates(Bundle outState, int[][] accordionIds, String statePrefix) {
        for (int i = 0; i < accordionIds.length; i++) {
            outState.putBoolean(statePrefix + "_" + i,
                    findViewById(accordionIds[i][1]).getVisibility() == View.VISIBLE);
        }
    }

    private void bindAccordionGroup(int[][] accordionIds, String[] labels,
                                    Bundle savedInstanceState, String statePrefix,
                                    boolean mainGroup) {
        boolean restoredExpandedItem = false;
        for (int i = 0; i < accordionIds.length; i++) {
            TextView header = findViewById(accordionIds[i][0]);
            View panel = findViewById(accordionIds[i][1]);
            String label = labels[i];
            boolean expanded = savedInstanceState != null
                    && savedInstanceState.getBoolean(statePrefix + "_" + i, false)
                    && !restoredExpandedItem;
            restoredExpandedItem |= expanded;
            setAccordionState(header, panel, label, expanded);
            header.setOnClickListener(view -> {
                boolean shouldExpand = panel.getVisibility() != View.VISIBLE;
                if (mainGroup) {
                    collapseAccordionGroup(MAIN_ACCORDION_IDS, MAIN_ACCORDION_LABELS);
                    collapseAccordionGroup(DETAIL_ACCORDION_IDS, DETAIL_ACCORDION_LABELS);
                } else {
                    collapseAccordionGroup(DETAIL_ACCORDION_IDS, DETAIL_ACCORDION_LABELS);
                }
                if (shouldExpand) {
                    setAccordionState(header, panel, label, true);
                }
            });
        }
    }

    private void collapseAccordionGroup(int[][] accordionIds, String[] labels) {
        for (int i = 0; i < accordionIds.length; i++) {
            setAccordionState(findViewById(accordionIds[i][0]),
                    findViewById(accordionIds[i][1]), labels[i], false);
        }
    }

    private void setAccordionState(TextView header, View panel, String label, boolean expanded) {
        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setText(label);
        int chevron = expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down;
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, chevron, 0);
        if (header.getId() == R.id.helpDiagnosticsDetailsHeader
                || header.getId() == R.id.helpPermissionDetailsHeader
                || header.getId() == R.id.helpSensorTechnicalHeader) {
            header.getCompoundDrawablesRelative()[2].setTint(getResources().getColor(R.color.ink));
        } else {
            header.getCompoundDrawablesRelative()[2].setTint(getResources().getColor(R.color.white));
        }
        header.setContentDescription(label + (expanded ? ", 접기" : ", 펼쳐서 보기"));
        header.setSelected(expanded);
    }

    private void returnToMain(String action) {
        Intent result = new Intent();
        result.putExtra(EXTRA_GUIDE_ACTION, action);
        setResult(RESULT_OK, result);
        finish();
    }

    private void openPocketInsertionVideo() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(POCKET_INSERTION_VIDEO_URL));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "동영상을 열 수 있는 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
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
