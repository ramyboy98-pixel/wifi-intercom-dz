@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(40, 60, 40, 40);
    root.setBackgroundColor(Color.parseColor("#EAF4FB"));

    TextView logo = new TextView(this);
    logo.setText("📡 WiFi Intercom DZ");
    logo.setTextSize(28);
    logo.setTextColor(Color.BLACK);

    TextView subtitle = new TextView(this);
    subtitle.setText("اتصال محلي عبر Wi-Fi");
    subtitle.setTextSize(16);
    subtitle.setTextColor(Color.DKGRAY);

    TextView status = new TextView(this);
    status.setText("🟢 جاهز للتحدث");
    status.setTextSize(18);
    status.setPadding(0, 40, 0, 40);

    Button ptt = new Button(this);
    ptt.setText("🎙️ اضغط مطولاً للتحدث");
    ptt.setTextSize(22);
    ptt.setAllCaps(false);
    ptt.setBackgroundColor(Color.parseColor("#2196F3"));
    ptt.setTextColor(Color.WHITE);

    LinearLayout.LayoutParams btnParams =
            new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    260
            );

    root.addView(logo);
    root.addView(subtitle);
    root.addView(status);
    root.addView(ptt, btnParams);

    setContentView(root);

    startReceiver();

    ptt.setOnTouchListener((v, event) -> {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            isTalking = true;

            status.setText("🔴 جاري الإرسال...");
            ptt.setText("🛑 حرر الزر لإيقاف الإرسال");
            ptt.setBackgroundColor(Color.parseColor("#D32F2F"));

            startSender();

            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {

            isTalking = false;

            status.setText("🟢 جاهز للتحدث");
            ptt.setText("🎙️ اضغط مطولاً للتحدث");
            ptt.setBackgroundColor(Color.parseColor("#2196F3"));

            return true;
        }

        return true;
    });
}
