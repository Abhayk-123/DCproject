import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class DigitalSignalGeneratorFullUI extends JFrame {
    private JComboBox<String> inputTypeBox, encodingBox, analogMethodBox, scramblingTypeBox;
    private JCheckBox scramblingCheckBox;
    private JTextField inputField;
    private JButton generateButton, decodeButton;
    private JPanel graphPanel;
    private JLabel resultLabel;
    private JTextArea detailsArea;

    private List<Integer> binaryData;
    private List<Double> time;
    private List<Double> signal;

    public DigitalSignalGeneratorFullUI() {
        setTitle("Digital Signal Generator - Advanced Version");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLayout(new BorderLayout(10, 10));

        // ==== TOP PANEL ====
        JPanel topPanel = new JPanel(new GridLayout(3, 4, 10, 10));
        topPanel.setBorder(new TitledBorder("Input Settings"));
        inputTypeBox = new JComboBox<>(new String[]{"Digital", "Analog"});
        encodingBox = new JComboBox<>(new String[]{
                "NRZ-L", "NRZ-I", "Manchester",
                "Differential Manchester", "AMI"
        });
        analogMethodBox = new JComboBox<>(new String[]{"PCM", "DM"});
        scramblingCheckBox = new JCheckBox("Scramble (AMI)");
        scramblingTypeBox = new JComboBox<>(new String[]{"B8ZS", "HDB3"});
        inputField = new JTextField();
        generateButton = new JButton("Generate Signal");
        decodeButton = new JButton("Decode Signal");

        topPanel.add(new JLabel("Input Type:"));
        topPanel.add(new JLabel("Encoding Scheme:"));
        topPanel.add(new JLabel("Digital/Analog Input:"));
        topPanel.add(new JLabel("Analog Method:"));
        topPanel.add(inputTypeBox);
        topPanel.add(encodingBox);
        topPanel.add(inputField);
        topPanel.add(analogMethodBox);
        topPanel.add(scramblingCheckBox);
        topPanel.add(new JLabel("Scramble Type:"));
        topPanel.add(new JLabel());
        topPanel.add(scramblingTypeBox);

        add(topPanel, BorderLayout.NORTH);

        // ==== GRAPH PANEL ====
        graphPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSignal((Graphics2D) g);
            }
        };
        graphPanel.setBackground(Color.black);
        graphPanel.setBorder(new TitledBorder("Signal Visualization"));
        add(graphPanel, BorderLayout.CENTER);

        // ==== RESULT PANEL ====
        JPanel bottomPanel = new JPanel(new BorderLayout());
        resultLabel = new JLabel("Enter data, choose options, then 'Generate Signal'");
        resultLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        bottomPanel.add(resultLabel, BorderLayout.NORTH);
        detailsArea = new JTextArea(5, 40);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(detailsArea);
        bottomPanel.add(sp, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // ==== ACTION ====
        inputTypeBox.addActionListener(e -> updateInputMode());
        scramblingCheckBox.addActionListener(e -> updateScrambleControls());
        generateButton.addActionListener(e -> generateSignal());
        decodeButton.addActionListener(e -> decodeSignal());
        JPanel rightButtons = new JPanel(new GridLayout(2, 1, 10, 10));
        rightButtons.add(generateButton);
        rightButtons.add(decodeButton);
        add(rightButtons, BorderLayout.EAST);
        updateInputMode();
        updateScrambleControls();
    }

    private void generateSignal() {
        String input = inputField.getText().trim();
        binaryData = new ArrayList<>();

        String inputType = inputTypeBox.getSelectedItem().toString();
        if ("Digital".equals(inputType)) {
            if (input.isEmpty() || !input.matches("[01]+")) {
                JOptionPane.showMessageDialog(this, "Enter valid binary data (0/1)!");
                return;
            }
            for (char c : input.toCharArray()) binaryData.add(c - '0');
        } else {
            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter analog samples (CSV), e.g., 0.1,0.3,0.9,-0.2");
                return;
            }
            List<Double> samples = parseAnalogSamples(input);
            if (samples.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Invalid analog samples. Use comma-separated numbers.");
                return;
            }
            String method = analogMethodBox.getSelectedItem().toString();
            if ("PCM".equals(method)) {
                binaryData = pcmEncode(samples, 8, -1.0, 1.0);
            } else {
                binaryData = deltaModulate(samples, 0.1);
            }
        }

        time = new ArrayList<>();
        signal = new ArrayList<>();

        String encoding = encodingBox.getSelectedItem().toString();
        boolean doScramble = scramblingCheckBox.isSelected() && "AMI".equals(encoding);
        String scrambleType = scramblingTypeBox.getSelectedItem() == null ? "B8ZS" : scramblingTypeBox.getSelectedItem().toString();

        if ("NRZ-L".equals(encoding)) encodeNRZL();
        else if ("NRZ-I".equals(encoding)) encodeNRZI();
        else if ("Manchester".equals(encoding)) encodeManchester();
        else if ("Differential Manchester".equals(encoding)) encodeDiffManchester();
        else if ("AMI".equals(encoding)) {
            if (doScramble) {
                if ("B8ZS".equals(scrambleType)) encodeAMIWithB8ZS();
                else encodeAMIWithHDB3();
            } else {
                encodeAMI();
            }
        }

        String bitString = bitsToString(binaryData);
        String longestPalindrome = manachersLongestPalindrome(bitString);
        int longestZeroRun = longestZeroRun(bitString);
        StringBuilder sb = new StringBuilder();
        sb.append("Digital bitstream: ").append(bitString).append('\n');
        sb.append("Longest palindrome: ").append(longestPalindrome)
          .append(" (len=").append(longestPalindrome.length()).append(")\n");
        sb.append("Longest 0-run: ").append(longestZeroRun).append('\n');
        if (doScramble) sb.append("Scrambling: ").append(scrambleType).append(" applied\n");
        detailsArea.setText(sb.toString());
        resultLabel.setText("Signal generated: " + encoding + (doScramble ? (" + " + scrambleType) : ""));
        repaint();
    }

    // ==== ENCODING METHODS ====
    private void encodeNRZL() {
        double t = 0;
        for (int bit : binaryData) {
            double level = bit == 1 ? 1 : -1;
            time.add(t);
            signal.add(level);
            t += 1;
            time.add(t);
            signal.add(level);
        }
    }

    private void encodeNRZI() {
        double t = 0, last = 1;
        for (int bit : binaryData) {
            if (bit == 1) last *= -1;
            time.add(t);
            signal.add(last);
            t += 1;
            time.add(t);
            signal.add(last);
        }
    }

    private void encodeManchester() {
        double t = 0;
        for (int bit : binaryData) {
            double first = bit == 1 ? 1 : -1;
            double second = -first;
            time.add(t);
            signal.add(first);
            t += 0.5;
            time.add(t);
            signal.add(first);
            time.add(t);
            signal.add(second);
            t += 0.5;
            time.add(t);
            signal.add(second);
        }
    }

    private void encodeDiffManchester() {
        double t = 0;
        double last = 1;
        for (int bit : binaryData) {
            double first, second;
            if (bit == 0) last *= -1;
            first = last;
            second = -last;
            time.add(t);
            signal.add(first);
            t += 0.5;
            time.add(t);
            signal.add(first);
            time.add(t);
            signal.add(second);
            t += 0.5;
            time.add(t);
            signal.add(second);
        }
    }

    private void encodeAMI() {
        double t = 0;
        double lastPulse = -1;
        for (int bit : binaryData) {
            double level;
            if (bit == 0) level = 0;
            else {
                lastPulse *= -1;
                level = lastPulse;
            }
            time.add(t);
            signal.add(level);
            t += 1;
            time.add(t);
            signal.add(level);
        }
    }

    private void encodeB8ZS() {
        List<Integer> data = new ArrayList<>(binaryData);
        for (int i = 0; i <= data.size() - 8; i++) {
            if (data.subList(i, i + 8).stream().allMatch(b -> b == 0)) {
                data.set(i + 3, 1);
                data.set(i + 4, -1);
                data.set(i + 5, 0);
                data.set(i + 6, 1);
            }
        }
        double t = 0, last = -1;
        for (int bit : data) {
            double level;
            if (bit == 0) level = 0;
            else {
                last *= -1;
                level = last;
            }
            time.add(t);
            signal.add(level);
            t += 1;
            time.add(t);
            signal.add(level);
        }
    }

    private void encodeHDB3() {
        List<Integer> data = new ArrayList<>(binaryData);
        double lastPulse = -1;
        int pulseCount = 0;

        for (int i = 0; i < data.size(); i++) {
            if (data.get(i) == 1) pulseCount++;
            if (i >= 3 && data.get(i) == 0 && data.get(i - 1) == 0 && data.get(i - 2) == 0 && data.get(i - 3) == 0) {
                if (pulseCount % 2 == 0) {
                    data.set(i - 3, 1);
                    data.set(i, -1);
                } else {
                    data.set(i, 1);
                }
                pulseCount = 0;
            }
        }

        double t = 0;
        for (int bit : data) {
            double level;
            if (bit == 0) level = 0;
            else {
                lastPulse *= -1;
                level = lastPulse;
            }
            time.add(t);
            signal.add(level);
            t += 1;
            time.add(t);
            signal.add(level);
        }
    }

    // ==== DRAW METHOD ====
    private void drawSignal(Graphics2D g2) {
        if (signal == null || signal.isEmpty()) return;

        int width = graphPanel.getWidth();
        int height = graphPanel.getHeight();

        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(2));

        double lastT = time.get(time.size() - 1);
        double scaleX = width / lastT;
        double scaleY = height / 4.0;
        double centerY = height / 2.0;

        // Draw grid: vertical per bit, horizontal at levels -1, 0, +1
        int numBits = (int) Math.round(lastT);
        Stroke oldStroke = g2.getStroke();
        g2.setColor(new Color(80, 80, 80));
        float[] dash = {4f, 4f};
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, dash, 0f));
        for (int b = 0; b <= numBits; b++) {
            int x = (int) Math.round(b * scaleX);
            g2.drawLine(x, 0, x, height);
        }
        // Horizontal lines at -1, 0, +1
        int yPlus = (int) Math.round(centerY - 1 * scaleY);
        int yZero = (int) Math.round(centerY - 0 * scaleY);
        int yMinus = (int) Math.round(centerY - (-1) * scaleY);
        g2.drawLine(0, yPlus, width, yPlus);
        g2.drawLine(0, yZero, width, yZero);
        g2.drawLine(0, yMinus, width, yMinus);
        // Restore stroke for signal
        g2.setStroke(oldStroke);

        Path2D path = new Path2D.Double();
        double x0 = time.get(0) * scaleX;
        double y0 = centerY - signal.get(0) * scaleY;
        path.moveTo(x0, y0);

        for (int i = 1; i < time.size(); i++) {
            double x = time.get(i) * scaleX;
            double y = centerY - signal.get(i) * scaleY;
            path.lineTo(x, y);
        }

        // Draw signal in green
        g2.setColor(Color.green);
        g2.draw(path);

        // Yellow markers at transitions
        g2.setColor(Color.yellow);
        for (int i = 1; i < signal.size(); i++) {
            if (!Objects.equals(signal.get(i), signal.get(i - 1))) {
                double x = time.get(i) * scaleX;
                double y = centerY - signal.get(i) * scaleY;
                g2.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
            }
        }
    }

    private void updateInputMode() {
        boolean isAnalog = "Analog".equals(inputTypeBox.getSelectedItem().toString());
        analogMethodBox.setEnabled(isAnalog);
        inputField.setToolTipText(isAnalog ? "Enter analog samples CSV in range [-1,1]" : "Enter binary string of 0/1");
    }

    private void updateScrambleControls() {
        boolean enable = scramblingCheckBox.isSelected();
        scramblingTypeBox.setEnabled(enable);
    }

    private void addLevelSegment(double t, double level) {
        time.add(t);
        signal.add(level);
        double tNext = t + 1;
        time.add(tNext);
        signal.add(level);
    }

    private List<Double> parseAnalogSamples(String csv) {
        List<Double> out = new ArrayList<>();
        String[] parts = csv.split(",");
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            try {
                out.add(Double.parseDouble(s));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private List<Integer> pcmEncode(List<Double> samples, int bits, double minVal, double maxVal) {
        int levels = (1 << bits);
        List<Integer> bitsOut = new ArrayList<>();
        for (double x : samples) {
            double clamped = Math.max(minVal, Math.min(maxVal, x));
            double norm = (clamped - minVal) / (maxVal - minVal);
            int q = (int) Math.floor(norm * (levels - 1));
            for (int b = bits - 1; b >= 0; b--) {
                bitsOut.add(((q >> b) & 1));
            }
        }
        return bitsOut;
    }

    private List<Integer> deltaModulate(List<Double> samples, double step) {
        if (samples.isEmpty()) return new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        double prev = samples.get(0);
        double approx = prev;
        for (int i = 1; i < samples.size(); i++) {
            double s = samples.get(i);
            int bit = s >= approx ? 1 : 0;
            out.add(bit);
            approx += bit == 1 ? step : -step;
        }
        return out;
    }

    private String bitsToString(List<Integer> bits) {
        StringBuilder sb = new StringBuilder(bits.size());
        for (int b : bits) sb.append(b == 0 ? '0' : '1');
        return sb.toString();
    }

    private String manachersLongestPalindrome(String s) {
        if (s.isEmpty()) return "";
        StringBuilder t = new StringBuilder("^");
        for (int i = 0; i < s.length(); i++) { t.append('#').append(s.charAt(i)); }
        t.append("#$");
        int n = t.length();
        int[] p = new int[n];
        int center = 0, right = 0;
        for (int i = 1; i < n - 1; i++) {
            int mir = 2 * center - i;
            p[i] = (right > i) ? Math.min(right - i, p[mir]) : 0;
            while (t.charAt(i + 1 + p[i]) == t.charAt(i - 1 - p[i])) p[i]++;
            if (i + p[i] > right) { center = i; right = i + p[i]; }
        }
        int maxLen = 0, centerIdx = 0;
        for (int i = 1; i < n - 1; i++) {
            if (p[i] > maxLen) { maxLen = p[i]; centerIdx = i; }
        }
        int start = (centerIdx - maxLen) / 2;
        return s.substring(start, start + maxLen);
    }

    private int longestZeroRun(String s) {
        int best = 0, cur = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '0') { cur++; best = Math.max(best, cur); }
            else cur = 0;
        }
        return best;
    }

    private void encodeAMIWithB8ZS() {
        double t = 0;
        int n = binaryData.size();
        int i = 0;
        // amiPolarity tracks last NORMAL AMI pulse (+1/-1)
        double amiPolarity = -1;
        // prevNonZero tracks last emitted nonzero (includes violations)
        double prevNonZero = amiPolarity;
        while (i < n) {
            if (i + 7 < n) {
                boolean eightZeros = true;
                for (int k = 0; k < 8; k++) {
                    if (binaryData.get(i + k) != 0) { eightZeros = false; break; }
                }
                if (eightZeros) {
                    // Emit 000 V B 0 V B with dynamic V/B depending on prevNonZero
                    addLevelSegment(t, 0); t += 1; // 0
                    addLevelSegment(t, 0); t += 1; // 0
                    addLevelSegment(t, 0); t += 1; // 0

                    // V1: same as prevNonZero (violation)
                    double V1 = prevNonZero == 0 ? 1 : prevNonZero;
                    addLevelSegment(t, V1); t += 1;
                    prevNonZero = V1; // track last emitted nonzero

                    // B1: normal AMI opposite of prevNonZero
                    double B1 = -prevNonZero;
                    addLevelSegment(t, B1); t += 1;
                    prevNonZero = B1;
                    amiPolarity = B1; // normal pulse updates AMI polarity

                    addLevelSegment(t, 0); t += 1; // 0

                    // V2: again same as prevNonZero (violation)
                    double V2 = prevNonZero;
                    addLevelSegment(t, V2); t += 1;
                    prevNonZero = V2;

                    // B2: opposite of prevNonZero
                    double B2 = -prevNonZero;
                    addLevelSegment(t, B2); t += 1;
                    prevNonZero = B2;
                    amiPolarity = B2; // update AMI polarity; ends equal to initial

                    i += 8;
                    continue;
                }
            }
            int bit = binaryData.get(i);
            if (bit == 0) {
                addLevelSegment(t, 0); t += 1;
            } else {
                // normal AMI alternation
                amiPolarity *= -1; if (amiPolarity == 0) amiPolarity = 1;
                addLevelSegment(t, amiPolarity); t += 1;
                prevNonZero = amiPolarity;
            }
            i++;
        }
    }

    private void encodeAMIWithHDB3() {
        double t = 0;
        int n = binaryData.size();
        int i = 0;
        double lastPulse = -1;
        int nonZeroSinceLastSub = 0;
        while (i < n) {
            if (i + 3 < n && binaryData.get(i) == 0 && binaryData.get(i + 1) == 0 && binaryData.get(i + 2) == 0 && binaryData.get(i + 3) == 0) {
                if (nonZeroSinceLastSub % 2 == 0) {
                    double b = -lastPulse; if (lastPulse == 0) b = 1; lastPulse = b;
                    addLevelSegment(t, b); t += 1;
                    addLevelSegment(t, 0); t += 1;
                    addLevelSegment(t, 0); t += 1;
                    double v = lastPulse; if (v == 0) v = 1;
                    addLevelSegment(t, v); t += 1;
                } else {
                    addLevelSegment(t, 0); t += 1;
                    addLevelSegment(t, 0); t += 1;
                    addLevelSegment(t, 0); t += 1;
                    double v = lastPulse; if (v == 0) v = 1;
                    addLevelSegment(t, v); t += 1;
                }
                nonZeroSinceLastSub = 0;
                i += 4;
            } else {
                int bit = binaryData.get(i);
                if (bit == 0) {
                    addLevelSegment(t, 0); t += 1;
                } else {
                    lastPulse *= -1; if (lastPulse == 0) lastPulse = 1;
                    addLevelSegment(t, lastPulse); t += 1;
                    nonZeroSinceLastSub++;
                }
                i++;
            }
        }
    }

    private void decodeSignal() {
        if (signal == null || signal.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No signal to decode.");
            return;
        }
        String encoding = encodingBox.getSelectedItem().toString();
        List<Integer> decoded;
        if ("NRZ-L".equals(encoding)) decoded = decodeNRZL();
        else if ("NRZ-I".equals(encoding)) decoded = decodeNRZI();
        else if ("Manchester".equals(encoding)) decoded = decodeManchester();
        else if ("Differential Manchester".equals(encoding)) decoded = decodeDiffManchester();
        else if ("AMI".equals(encoding)) decoded = decodeAMIBase();
        else decoded = new ArrayList<>();
        String decodedStr = bitsToString(decoded);
        detailsArea.append("Decoded bitstream: " + decodedStr + "\n");
        JOptionPane.showMessageDialog(this, "Decoded bits length: " + decoded.size());
    }

    private List<Integer> decodeNRZL() {
        List<Integer> out = new ArrayList<>();
        int numBits = (int) Math.round(time.get(time.size() - 1));
        for (int b = 0; b < numBits; b++) {
            double tSample = b + 0.5;
            double level = interpolateLevelAt(tSample);
            out.add(level > 0 ? 1 : 0);
        }
        return out;
    }

    private List<Integer> decodeNRZI() {
        List<Integer> out = new ArrayList<>();
        int numBits = (int) Math.round(time.get(time.size() - 1));
        double lastLevel = interpolateLevelAt(0.25);
        for (int b = 0; b < numBits; b++) {
            double tEnd = b + 0.99;
            double levelEnd = interpolateLevelAt(tEnd);
            out.add(Math.signum(levelEnd) != Math.signum(lastLevel) ? 1 : 0);
            lastLevel = levelEnd;
        }
        return out;
    }

    private List<Integer> decodeManchester() {
        List<Integer> out = new ArrayList<>();
        int numBits = (int) Math.round(time.get(time.size() - 1));
        for (int b = 0; b < numBits; b++) {
            double firstHalf = interpolateLevelAt(b + 0.25);
            double secondHalf = interpolateLevelAt(b + 0.75);
            out.add(firstHalf > secondHalf ? 1 : 0);
        }
        return out;
    }

    private List<Integer> decodeDiffManchester() {
        List<Integer> out = new ArrayList<>();
        int numBits = (int) Math.round(time.get(time.size() - 1));
        double lastMid = interpolateLevelAt(0.5);
        for (int b = 0; b < numBits; b++) {
            double tMid = b + 0.5;
            double midLevel = interpolateLevelAt(tMid);
            out.add(Math.signum(midLevel) == Math.signum(lastMid) ? 1 : 0);
            lastMid = midLevel;
        }
        return out;
    }

    private List<Integer> decodeAMIBase() {
        List<Integer> out = new ArrayList<>();
        int numBits = (int) Math.round(time.get(time.size() - 1));
        for (int b = 0; b < numBits; b++) {
            double level = interpolateLevelAt(b + 0.5);
            out.add(Math.abs(level) > 1e-6 ? 1 : 0);
        }
        return out;
    }

    private double interpolateLevelAt(double tx) {
        for (int i = 1; i < time.size(); i++) {
            if (tx <= time.get(i)) return signal.get(i);
        }
        return signal.get(signal.size() - 1);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DigitalSignalGeneratorFullUI().setVisible(true));
    }
}
