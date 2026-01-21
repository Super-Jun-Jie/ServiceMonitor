package com.love.ui;

import com.love.model.AppSettings;
import com.love.model.ServiceConfig;
import com.love.util.ConfigManager;
import com.love.util.SettingsManager;
import com.love.util.SimpleProcessWatcher;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceMonitorFrame extends JFrame {
    
    private DefaultTableModel tableModel;
    private JTable serviceTable;
    private List<ServiceConfig> configs;
    private Map<Integer, SimpleProcessWatcher> watchers;
    private Map<Integer, ScheduledExecutorService> statusUpdateServices;
    
    private JTextArea logArea;
    private ScheduledExecutorService globalStatusUpdateService;
    private AppSettings appSettings;
    private Set<Integer> startingServices = new HashSet<>(); // 正在启动的服务索引

    public ServiceMonitorFrame() {
        configs = new ArrayList<>();
        watchers = new HashMap<>();
        statusUpdateServices = new HashMap<>();
        appSettings = SettingsManager.loadSettings();
        
        initComponents();
        loadConfigs();
        setupLayout();
        setupEvents();
        startGlobalStatusUpdate();
    }

    // 蓝色系配色方案
    private static final Color BLUE_PRIMARY = new Color(33, 150, 243);     // 蓝色
    private static final Color BLUE_LIGHT = new Color(144, 202, 249);     // 淡蓝色
    private static final Color BLUE_DARK = new Color(25, 118, 210);       // 深蓝色
    private static final Color BACKGROUND = new Color(250, 250, 255);     // 淡蓝白背景
    private static final Color PANEL = new Color(240, 247, 255);          // 淡蓝色面板
    private static final Color TEXT = new Color(33, 33, 33);             // 深灰色文字
    private static final Color BORDER = new Color(187, 222, 251);         // 淡蓝色边框
    private static final Color SUCCESS = new Color(76, 175, 80);          // 绿色（运行中）
    private static final Color ERROR_COLOR = new Color(244, 67, 54);      // 红色（错误）
    
    private void initComponents() {
        setTitle("服务监控器 - ServiceMonitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        
        // 设置窗口背景色
        getContentPane().setBackground(BACKGROUND);
        
        // 使用自定义标题栏，在系统按钮前面添加设置按钮
        setupCustomTitleBar();
        
        // 表格模型
        String[] columnNames = {"名称", "Java路径", "工作目录", "状态", "PID", "操作"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        serviceTable = new JTable(tableModel);
        serviceTable.setRowHeight(35);
        serviceTable.getColumn("操作").setCellRenderer(new ButtonCellRenderer());
        
        // 添加鼠标监听器处理按钮点击
        serviceTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = serviceTable.rowAtPoint(e.getPoint());
                int col = serviceTable.columnAtPoint(e.getPoint());
                
                if (row >= 0 && col == 5) { // 操作列
                    int index = (Integer) tableModel.getValueAt(row, 5);
                    SimpleProcessWatcher watcher = watchers.get(index);
                    boolean isRunning = watcher != null && watcher.isRunning();
                    
                    // 根据点击位置判断点击的是哪个按钮
                    Rectangle cellRect = serviceTable.getCellRect(row, col, false);
                    int x = e.getX() - cellRect.x;
                    
                    if (isRunning) {
                        // 运行中：停止(2-52)、重启(57-107)、编辑(112-162)、删除(167-217)
                        if (x >= 2 && x < 57) {
                            stopService(index);
                        } else if (x >= 57 && x < 112) {
                            restartService(index);
                        } else if (x >= 112 && x < 167) {
                            editService(index);
                        } else if (x >= 167 && x < 217) {
                            deleteService(index);
                        }
                    } else {
                        // 未运行：启动(2-52)、编辑(57-107)、删除(112-162)
                        if (x >= 2 && x < 57) {
                            startService(index);
                        } else if (x >= 57 && x < 112) {
                            editService(index);
                        } else if (x >= 112 && x < 162) {
                            deleteService(index);
                        }
                    }
                }
            }
        });
        
        // 日志区域
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    private JPanel customTitleBar;
    private JButton titleBarSettingsBtn;
    
    private void setupCustomTitleBar() {
        // 移除系统标题栏装饰
        setUndecorated(true);
        
        // 创建自定义标题栏（蓝色渐变，圆角）
        customTitleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 蓝色渐变背景（顶部圆角）
                GradientPaint gradient = new GradientPaint(
                    0, 0, BLUE_PRIMARY,
                    getWidth(), 0, BLUE_LIGHT
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2d.fillRect(0, 15, getWidth(), getHeight() - 15); // 填充下方矩形部分
                g2d.dispose();
            }
        };
        customTitleBar.setPreferredSize(new Dimension(getWidth(), 40));
        customTitleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BLUE_DARK));
        
        // 左侧：标题（带图标效果）
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        titlePanel.setOpaque(false);
        JLabel iconLabel = new JLabel("⚡");
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titlePanel.add(iconLabel);
        JLabel titleLabel = new JLabel("服务监控器");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        titlePanel.add(titleLabel);
        customTitleBar.add(titlePanel, BorderLayout.WEST);
        
        // 右侧：按钮区域（设置、最小化、最大化、关闭）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setOpaque(false);
        
        // 设置按钮
        titleBarSettingsBtn = createTitleBarButton("⚙", "设置");
        titleBarSettingsBtn.addActionListener(e -> showSettingsDialog());
        buttonPanel.add(titleBarSettingsBtn);
        
        // 最小化按钮
        JButton minimizeBtn = createTitleBarButton("—", "最小化");
        minimizeBtn.addActionListener(e -> setState(JFrame.ICONIFIED));
        buttonPanel.add(minimizeBtn);
        
        // 最大化/还原按钮
        JButton maximizeBtn = createTitleBarButton("□", "最大化");
        maximizeBtn.addActionListener(e -> {
            if (getExtendedState() == JFrame.MAXIMIZED_BOTH) {
                setExtendedState(JFrame.NORMAL);
                maximizeBtn.setText("□");
                maximizeBtn.setToolTipText("最大化");
            } else {
                setExtendedState(JFrame.MAXIMIZED_BOTH);
                maximizeBtn.setText("❐");
                maximizeBtn.setToolTipText("还原");
            }
        });
        buttonPanel.add(maximizeBtn);
        
        // 关闭按钮
        JButton closeBtn = createTitleBarButton("✕", "关闭");
        closeBtn.setForeground(Color.RED);
        closeBtn.addActionListener(e -> {
            processWindowEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING));
        });
        buttonPanel.add(closeBtn);
        
        customTitleBar.add(buttonPanel, BorderLayout.EAST);
        
        // 添加标题栏到窗口顶部
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND);
        
        // 添加窗口圆角边框
        ((JPanel) getContentPane()).setBorder(new javax.swing.border.Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(BLUE_DARK);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(x, y, width - 1, height - 1, 15, 15);
                g2d.dispose();
            }
            
            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(2, 2, 2, 2);
            }
            
            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        });
        
        getContentPane().add(customTitleBar, BorderLayout.NORTH);
        
        // 添加鼠标拖动功能
        final int[] dragStartX = new int[1];
        final int[] dragStartY = new int[1];
        final int[] frameStartX = new int[1];
        final int[] frameStartY = new int[1];
        
        customTitleBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragStartX[0] = e.getXOnScreen();
                dragStartY[0] = e.getYOnScreen();
                frameStartX[0] = getX();
                frameStartY[0] = getY();
            }
        });
        
        customTitleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                int deltaX = e.getXOnScreen() - dragStartX[0];
                int deltaY = e.getYOnScreen() - dragStartY[0];
                setLocation(frameStartX[0] + deltaX, frameStartY[0] + deltaY);
            }
        });
    }

    private JButton createTitleBarButton(String text, String tooltip) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isRollover() || getModel().isPressed()) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getText().equals("✕")) {
                    g2d.setColor(new Color(244, 67, 54, 200));
                } else {
                    g2d.setColor(new Color(255, 255, 255, 120));
                }
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                    g2d.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(50, 40));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setForeground(Color.WHITE);
                btn.repaint();
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setForeground(Color.WHITE);
                btn.repaint();
            }
        });
        
        return btn;
    }

    private void setupLayout() {
        // 注意：标题栏已经在setupCustomTitleBar中添加，这里只设置内容区域
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(BACKGROUND);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 顶部：工具栏（圆角样式）
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(BORDER);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2d.dispose();
            }
        };
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton addBtn = createStyledButton("➕ 添加服务", BLUE_PRIMARY);
        addBtn.addActionListener(e -> showAddDialog());
        toolbar.add(addBtn);
        
        JButton refreshBtn = createStyledButton("🔄 刷新配置", BLUE_LIGHT);
        refreshBtn.addActionListener(e -> reloadConfigs());
        toolbar.add(refreshBtn);
        
        toolbar.add(Box.createHorizontalStrut(20)); // 分隔符
        
        JButton startAllBtn = createStyledButton("▶ 一键启动所有", SUCCESS);
        startAllBtn.addActionListener(e -> startAllServices());
        toolbar.add(startAllBtn);
        
        JButton stopAllBtn = createStyledButton("⏹ 一键停止所有", ERROR_COLOR);
        stopAllBtn.addActionListener(e -> stopAllServices());
        toolbar.add(stopAllBtn);
        
        contentPanel.add(toolbar, BorderLayout.NORTH);
        
        // 中部：服务列表（圆角卡片样式）
        JPanel tablePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(PANEL);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2d.setColor(BLUE_DARK);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2d.setColor(BORDER);
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
                g2d.dispose();
            }
        };
        tablePanel.setOpaque(false);
        tablePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 设置表格样式
        serviceTable.setBackground(Color.WHITE);
        serviceTable.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        serviceTable.setRowHeight(40);
        serviceTable.setSelectionBackground(BLUE_LIGHT);
        serviceTable.setSelectionForeground(TEXT);
        serviceTable.setGridColor(BORDER);
        serviceTable.getTableHeader().setBackground(BLUE_PRIMARY);
        serviceTable.getTableHeader().setForeground(Color.WHITE);
        serviceTable.getTableHeader().setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        serviceTable.getTableHeader().setPreferredSize(new Dimension(0, 35));
        
        JScrollPane tableScrollPane = new JScrollPane(serviceTable);
        tableScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(),
                "📋 服务列表",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 14),
                TEXT
            ),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        tableScrollPane.setOpaque(false);
        tableScrollPane.getViewport().setBackground(Color.WHITE);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        contentPanel.add(tablePanel, BorderLayout.CENTER);
        
        // 底部：日志面板（圆角卡片样式）
        JPanel logPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(PANEL);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2d.setColor(BLUE_DARK);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2d.setColor(BORDER);
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
                g2d.dispose();
            }
        };
        logPanel.setOpaque(false);
        logPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(TEXT);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(),
                "📝 运行日志",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 14),
                TEXT
            ),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        logScrollPane.setOpaque(false);
        logScrollPane.getViewport().setBackground(Color.WHITE);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        
        JPanel logButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        logButtonPanel.setOpaque(false);
        JButton clearLogBtn = createStyledButton("🗑 清空日志", new Color(158, 158, 158));
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logButtonPanel.add(clearLogBtn);
        logPanel.add(logButtonPanel, BorderLayout.SOUTH);
        
        contentPanel.add(logPanel, BorderLayout.SOUTH);
        
        // 将内容面板添加到窗口（标题栏已经在setupCustomTitleBar中添加）
        getContentPane().add(contentPanel, BorderLayout.CENTER);
    }

    private void setupEvents() {
        // 窗口关闭时保存配置
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // 检查是否有正在运行的服务
                boolean hasRunningService = false;
                int runningCount = 0;
                for (SimpleProcessWatcher watcher : watchers.values()) {
                    if (watcher != null && watcher.isRunning()) {
                        hasRunningService = true;
                        runningCount++;
                    }
                }
                
                if (hasRunningService) {
                    // 显示确认对话框
                    int confirm = JOptionPane.showConfirmDialog(
                        ServiceMonitorFrame.this,
                        "当前有 " + runningCount + " 个服务正在运行，关闭窗口将停止所有服务。\n确定要关闭吗？",
                        "确认关闭",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    
                    if (confirm != JOptionPane.YES_OPTION) {
                        // 用户取消，不关闭窗口
                        return;
                    }
                }
                
                // 用户确认，停止所有服务并退出
                saveConfigs();
                appendLog("正在停止所有服务...");
                
                // 停止所有监控
                int stoppedCount = 0;
                for (SimpleProcessWatcher watcher : watchers.values()) {
                    if (watcher != null && watcher.isRunning()) {
                        watcher.stop();
                        stoppedCount++;
                    }
                }
                
                appendLog("已停止 " + stoppedCount + " 个服务");
                
                // 关闭线程池
                if (globalStatusUpdateService != null) {
                    globalStatusUpdateService.shutdown();
                }
                for (ScheduledExecutorService service : statusUpdateServices.values()) {
                    if (service != null) {
                        service.shutdown();
                    }
                }
                
                // 等待所有服务停止（最多等待5秒）
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 5000) {
                    boolean allStopped = true;
                    for (SimpleProcessWatcher watcher : watchers.values()) {
                        if (watcher != null && watcher.isRunning()) {
                            allStopped = false;
                            break;
                        }
                    }
                    if (allStopped) {
                        break;
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                appendLog("正在退出...");
                System.exit(0);
            }
        });
    }

    private void loadConfigs() {
        configs = ConfigManager.loadConfigs();
        refreshTable();
    }

    private void reloadConfigs() {
        // 保存当前运行状态（按服务名称映射）
        Map<String, SimpleProcessWatcher> nameToWatcher = new HashMap<>();
        Map<String, ScheduledExecutorService> nameToService = new HashMap<>();
        
        for (int i = 0; i < configs.size(); i++) {
            if (i < configs.size()) {
                String name = configs.get(i).getName();
                SimpleProcessWatcher watcher = watchers.get(i);
                if (watcher != null && watcher.isRunning()) {
                    nameToWatcher.put(name, watcher);
                }
                ScheduledExecutorService service = statusUpdateServices.get(i);
                if (service != null) {
                    nameToService.put(name, service);
                }
            }
        }
        
        // 重新加载配置
        List<ServiceConfig> newConfigs = ConfigManager.loadConfigs();
        
        // 停止所有旧的状态更新服务（稍后重新映射）
        for (ScheduledExecutorService service : statusUpdateServices.values()) {
            if (service != null) {
                service.shutdown();
            }
        }
        
        // 重新构建watchers和services映射
        watchers.clear();
        statusUpdateServices.clear();
        
        // 通过服务名称匹配，保留运行状态
        for (int i = 0; i < newConfigs.size(); i++) {
            ServiceConfig newConfig = newConfigs.get(i);
            String name = newConfig.getName();
            
            // 如果该服务之前正在运行，保留运行状态
            SimpleProcessWatcher watcher = nameToWatcher.get(name);
            if (watcher != null && watcher.isRunning()) {
                watchers.put(i, watcher);
                
                // 重新创建状态更新服务
                ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
                statusUpdateServices.put(i, service);
                service.scheduleAtFixedRate(() -> {
                    SwingUtilities.invokeLater(() -> {
                        refreshTable();
                    });
                }, 0, 1, TimeUnit.SECONDS);
            }
        }
        
        // 停止那些在新配置中找不到的服务
        for (Map.Entry<String, SimpleProcessWatcher> entry : nameToWatcher.entrySet()) {
            boolean found = false;
            for (ServiceConfig config : newConfigs) {
                if (config.getName().equals(entry.getKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 服务已被删除，停止它
                SimpleProcessWatcher watcher = entry.getValue();
                if (watcher != null) {
                    watcher.stop();
                }
            }
        }
        
        // 更新配置列表
        configs = newConfigs;
        refreshTable();
        appendLog("配置已重新加载，共 " + configs.size() + " 个服务");
    }

    private void saveConfigs() {
        try {
            ConfigManager.saveConfigs(configs);
            appendLog("配置已保存");
        } catch (Exception e) {
            appendLog("保存配置失败: " + e.getMessage());
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (int i = 0; i < configs.size(); i++) {
            ServiceConfig config = configs.get(i);
            SimpleProcessWatcher watcher = watchers.get(i);
            
            String status = "未启动";
            String pid = "-";
            
            // 检查是否正在启动
            if (startingServices.contains(i)) {
                status = "🟡 启动中...";
            } else if (watcher != null) {
                // 优先检查进程是否存活（更准确反映实际状态）
                if (watcher.isProcessAlive()) {
                    status = "🟢 运行中";
                    pid = String.valueOf(watcher.getProcessId());
                } else if (watcher.isRunning()) {
                    // 监控在运行但进程已死（可能是被外部杀死）
                    status = "🟡 进程已退出";
                    pid = "-";
                } else {
                    status = "⚪ 已停止";
                    pid = "-";
                }
            } else {
                status = "⚪ 未启动";
            }
            
            Object[] row = {
                config.getName(),
                config.getJavaExe(),
                config.getWorkDir(),
                status,
                pid,
                i  // 存储索引用于操作按钮
            };
            tableModel.addRow(row);
        }
        
        // 设置状态列的颜色渲染
        serviceTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // 状态列特殊处理
                if (column == 3 && value != null) {
                    String status = value.toString();
                    if (status.contains("运行中")) {
                        c.setBackground(new Color(200, 230, 201)); // 浅绿色
                        c.setForeground(new Color(27, 94, 32)); // 深绿色
                    } else if (status.contains("启动中")) {
                        c.setBackground(new Color(255, 243, 224)); // 浅黄色
                        c.setForeground(new Color(230, 126, 34)); // 深黄色
                        // 添加闪烁效果提示
                        ((JLabel) c).setText(status + " ⏳");
                    } else if (status.contains("已退出")) {
                        c.setBackground(new Color(255, 243, 224)); // 浅黄色
                        c.setForeground(new Color(230, 126, 34)); // 深黄色
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(TEXT);
                    }
                } else {
                    if (isSelected) {
                        c.setBackground(BLUE_LIGHT);
                        c.setForeground(TEXT);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : PANEL);
                        c.setForeground(TEXT);
                    }
                }
                
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
                ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                return c;
            }
        });
    }

    private void showAddDialog() {
        AddServiceDialog dialog = new AddServiceDialog(this);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            ServiceConfig config = dialog.getConfig();
            if (config != null) {
                configs.add(config);
                saveConfigs();
                refreshTable();
                appendLog("已添加服务: " + config.getName());
            }
        }
    }

    private void startService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        ServiceConfig config = configs.get(index);
        if (watchers.containsKey(index) && watchers.get(index).isRunning()) {
            appendLog("服务 " + config.getName() + " 已在运行中");
            return;
        }
        
        // 如果正在启动，忽略重复点击
        if (startingServices.contains(index)) {
            appendLog("服务 " + config.getName() + " 正在启动中，请稍候...");
            return;
        }
        
        // 标记为正在启动
        startingServices.add(index);
        
        // 立即更新UI显示"启动中"状态
        SwingUtilities.invokeLater(() -> {
            refreshTable();
        });
        
        appendLog("正在启动服务: " + config.getName() + "...");
        
        // 在后台线程执行启动，避免阻塞UI
        new Thread(() -> {
            try {
                String[] args = config.getArgsArray();
                
                // 自动生成日志路径：logs/服务名称/
                String logBasePath = appSettings.getLogBasePath();
                File logDir = new File(logBasePath, config.getName());
                logDir.mkdirs(); // 确保目录存在
                
                File outLog = new File(logDir, "output.log");
                File errLog = new File(logDir, "error.log");
                
                SimpleProcessWatcher watcher = new SimpleProcessWatcher(
                    config.getJavaExe(),
                    config.getWorkDir(),
                    args,
                    outLog,
                    errLog
                );
                
                watcher.setLogCallback(msg -> appendLog("[" + config.getName() + "] " + msg));
                
                // start() 方法现在会等待5秒并确认进程真正启动成功
                // 如果进程在5秒内退出（如端口占用），会抛出IOException
                watcher.start();
                
                watchers.put(index, watcher);
                startStatusUpdate(index);
                
                // 只有在确认启动成功后才显示成功消息
                appendLog("✓ 服务 " + config.getName() + " 启动成功");
            } catch (Exception e) {
                appendLog("✗ 启动服务 " + config.getName() + " 失败: " + e.getMessage());
            } finally {
                // 移除启动中标记
                startingServices.remove(index);
                // 刷新表格
                SwingUtilities.invokeLater(() -> {
                    refreshTable();
                });
            }
        }, "StartService-" + config.getName()).start();
    }

    private void stopService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        ServiceConfig config = configs.get(index);
        SimpleProcessWatcher watcher = watchers.get(index);
        
        if (watcher != null) {
            watcher.stop();
            watchers.remove(index);
            
            ScheduledExecutorService service = statusUpdateServices.remove(index);
            if (service != null) {
                service.shutdown();
            }
            
            appendLog("服务 " + config.getName() + " 已停止");
            refreshTable();
        }
    }

    private void startAllServices() {
        int totalCount = configs.size();
        if (totalCount == 0) {
            appendLog("没有可启动的服务");
            return;
        }
        
        // 在后台线程执行，避免阻塞UI
        new Thread(() -> {
            int startedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
            
            appendLog("开始一键启动所有服务，共 " + totalCount + " 个服务...");
            
            for (int i = 0; i < configs.size(); i++) {
                SimpleProcessWatcher watcher = watchers.get(i);
                
                // 如果服务已经在运行，跳过
                if (watcher != null && watcher.isProcessAlive()) {
                    skippedCount++;
                    appendLog("服务 " + configs.get(i).getName() + " 已在运行，跳过");
                    continue;
                }
                
                // 启动服务
                try {
                    final int index = i;
                    SwingUtilities.invokeLater(() -> startService(index));
                    startedCount++;
                    // 每个服务启动间隔500ms，避免同时启动过多服务
                    Thread.sleep(500);
                } catch (Exception e) {
                    failedCount++;
                    appendLog("启动服务 " + configs.get(i).getName() + " 失败: " + e.getMessage());
                }
            }
            
            appendLog("一键启动完成：成功 " + startedCount + " 个，跳过 " + skippedCount + " 个，失败 " + failedCount + " 个");
            SwingUtilities.invokeLater(() -> refreshTable());
        }, "StartAllServices").start();
    }

    private void stopAllServices() {
        // 统计运行中的服务数量
        int count = 0;
        for (SimpleProcessWatcher watcher : watchers.values()) {
            if (watcher != null && watcher.isProcessAlive()) {
                count++;
            }
        }
        
        final int totalCount = count; // 声明为final供lambda使用
        
        if (totalCount == 0) {
            appendLog("没有正在运行的服务");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "确定要停止所有 " + totalCount + " 个正在运行的服务吗？",
            "确认停止所有",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // 在后台线程执行，避免阻塞UI
        new Thread(() -> {
            int stoppedCount = 0;
            
            appendLog("开始一键停止所有服务，共 " + totalCount + " 个服务...");
            
            // 收集所有需要停止的服务索引
            List<Integer> indicesToStop = new ArrayList<>();
            for (int i = 0; i < configs.size(); i++) {
                SimpleProcessWatcher watcher = watchers.get(i);
                if (watcher != null && watcher.isProcessAlive()) {
                    indicesToStop.add(i);
                }
            }
            
            // 停止所有服务
            for (int index : indicesToStop) {
                try {
                    final int idx = index;
                    SwingUtilities.invokeLater(() -> stopService(idx));
                    stoppedCount++;
                    // 每个服务停止间隔200ms
                    Thread.sleep(200);
                } catch (Exception e) {
                    appendLog("停止服务 " + configs.get(index).getName() + " 失败: " + e.getMessage());
                }
            }
            
            appendLog("一键停止完成：已停止 " + stoppedCount + " 个服务");
            SwingUtilities.invokeLater(() -> refreshTable());
        }, "StopAllServices").start();
    }

    private void restartService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        ServiceConfig config = configs.get(index);
        appendLog("正在重启服务: " + config.getName());
        stopService(index);
        
        // 延迟1秒后启动
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                SwingUtilities.invokeLater(() -> startService(index));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void editService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        ServiceConfig config = configs.get(index);
        SimpleProcessWatcher watcher = watchers.get(index);
        
        // 如果服务正在运行，提示先停止
        if (watcher != null && watcher.isProcessAlive()) {
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "服务 \"" + config.getName() + "\" 正在运行，编辑配置需要先停止服务。\n是否停止并编辑？",
                "确认停止",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            
            // 停止服务
            stopService(index);
            
            // 等待服务停止
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 显示编辑对话框
        EditServiceDialog dialog = new EditServiceDialog(this, config, index);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            ServiceConfig newConfig = dialog.getConfig();
            if (newConfig != null) {
                configs.set(index, newConfig);
                saveConfigs();
                refreshTable();
                appendLog("已更新服务配置: " + newConfig.getName());
            }
        }
    }

    private void deleteService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        ServiceConfig config = configs.get(index);
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "确定要删除服务 \"" + config.getName() + "\" 吗？",
            "确认删除",
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            // 先停止服务
            stopService(index);
            
            // 移除配置
            configs.remove(index);
            
            // 更新watchers的索引
            Map<Integer, SimpleProcessWatcher> newWatchers = new HashMap<>();
            for (Map.Entry<Integer, SimpleProcessWatcher> entry : watchers.entrySet()) {
                int oldIndex = entry.getKey();
                if (oldIndex < index) {
                    newWatchers.put(oldIndex, entry.getValue());
                } else if (oldIndex > index) {
                    newWatchers.put(oldIndex - 1, entry.getValue());
                }
            }
            watchers = newWatchers;
            
            // 更新statusUpdateServices的索引
            Map<Integer, ScheduledExecutorService> newServices = new HashMap<>();
            for (Map.Entry<Integer, ScheduledExecutorService> entry : statusUpdateServices.entrySet()) {
                int oldIndex = entry.getKey();
                if (oldIndex < index) {
                    newServices.put(oldIndex, entry.getValue());
                } else if (oldIndex > index) {
                    newServices.put(oldIndex - 1, entry.getValue());
                } else {
                    entry.getValue().shutdown();
                }
            }
            statusUpdateServices = newServices;
            
            saveConfigs();
            refreshTable();
            appendLog("已删除服务: " + config.getName());
        }
    }

    private void startStatusUpdate(int index) {
        // 如果已存在，先关闭旧的
        ScheduledExecutorService oldService = statusUpdateServices.remove(index);
        if (oldService != null) {
            oldService.shutdown();
        }
        
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StatusUpdate-" + index);
            t.setDaemon(true);
            return t;
        });
        statusUpdateServices.put(index, service);
        
        // 提高刷新频率到1秒，确保能及时检测到外部杀死进程的情况
        service.scheduleAtFixedRate(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    try {
                        refreshTable();
                    } catch (Exception e) {
                        // 防止UI更新异常影响后台任务
                        System.err.println("刷新表格时出错: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                // 防止任务执行异常
                System.err.println("状态更新任务出错: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void startGlobalStatusUpdate() {
        if (globalStatusUpdateService != null) {
            globalStatusUpdateService.shutdown();
        }
        
        globalStatusUpdateService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GlobalStatusUpdate");
            t.setDaemon(true);
            return t;
        });
        
        // 提高刷新频率到1秒，确保能及时检测到外部杀死进程的情况
        globalStatusUpdateService.scheduleAtFixedRate(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    try {
                        refreshTable();
                    } catch (Exception e) {
                        // 防止UI更新异常影响后台任务
                        System.err.println("刷新表格时出错: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                // 防止任务执行异常
                System.err.println("全局状态更新任务出错: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void appendLog(String message) {
        if (message == null) {
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (logArea != null) {
                        logArea.append(java.time.LocalDateTime.now() + " | " + message + "\n");
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    }
                } catch (Exception e) {
                    // 防止日志追加异常影响程序运行
                    System.err.println("追加日志失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            // 防止invokeLater异常
            System.err.println("调度日志追加失败: " + e.getMessage());
        }
    }

    /**
     * 创建表格中使用的按钮（带悬停效果，暖色主题）
     */
    private JButton createTableButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color paintColor = bgColor;
                if (getModel().isPressed()) {
                    paintColor = bgColor.darker();
                } else if (getModel().isRollover()) {
                    paintColor = bgColor.brighter();
                }
                
                g2d.setColor(paintColor);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        // 设置按钮样式
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        return button;
    }

    /**
     * 创建带悬停效果的按钮（工具栏用，带颜色）
     */
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绘制圆角背景
                if (getModel().isPressed()) {
                    g2d.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(bgColor.brighter());
                } else {
                    g2d.setColor(bgColor);
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                
                // 绘制阴影效果
                g2d.setColor(new Color(0, 0, 0, 20));
                g2d.fillRoundRect(2, 2, getWidth(), getHeight(), 8, 8);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        // 设置按钮样式
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(button.getPreferredSize().width + 20, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // 鼠标悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.repaint();
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.repaint();
            }
        });
        
        return button;
    }
    
    /**
     * 创建带悬停效果的按钮（对话框用，默认样式）
     */
    private JButton createStyledButton(String text) {
        return createStyledButton(text, BLUE_PRIMARY);
    }

    // 按钮渲染器
    private class ButtonCellRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private JButton startBtn;
        private JButton stopBtn;
        private JButton restartBtn;
        private JButton editBtn;
        private JButton deleteBtn;

        public ButtonCellRenderer() {
            setLayout(null); // 使用绝对布局以便精确定位
            setOpaque(true);
            
            startBtn = ServiceMonitorFrame.this.createTableButton("启动", SUCCESS); // 绿色
            startBtn.setBounds(2, 5, 50, 25);
            stopBtn = ServiceMonitorFrame.this.createTableButton("停止", ERROR_COLOR); // 红色
            stopBtn.setBounds(2, 5, 50, 25);
            restartBtn = ServiceMonitorFrame.this.createTableButton("重启", BLUE_LIGHT); // 淡蓝色
            restartBtn.setBounds(57, 5, 50, 25);
            editBtn = ServiceMonitorFrame.this.createTableButton("编辑", BLUE_PRIMARY); // 蓝色
            editBtn.setBounds(112, 5, 50, 25);
            deleteBtn = ServiceMonitorFrame.this.createTableButton("删除", new Color(158, 158, 158)); // 灰色
            deleteBtn.setBounds(167, 5, 50, 25);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            removeAll();
            int index = (Integer) value;
            SimpleProcessWatcher watcher = watchers.get(index);
            boolean isRunning = watcher != null && watcher.isRunning();
            
            if (isRunning) {
                add(stopBtn);
                add(restartBtn);
                editBtn.setBounds(112, 5, 50, 25);
                deleteBtn.setBounds(167, 5, 50, 25);
            } else {
                add(startBtn);
                editBtn.setBounds(57, 5, 50, 25);
                deleteBtn.setBounds(112, 5, 50, 25);
            }
            add(editBtn);
            add(deleteBtn);
            
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }


    // 添加服务对话框
    private class AddServiceDialog extends JDialog {
        private boolean confirmed = false;
        private ServiceConfig config;
        
        private JTextField nameField;
        private JTextField javaExeField;
        private JTextField workDirField;
        private JTextArea argsArea;

        public AddServiceDialog(JFrame parent) {
            super(parent, "添加服务", true);
            setSize(600, 500);
            setLocationRelativeTo(parent);
            
            initComponents();
            setupLayout();
        }

        private void initComponents() {
            nameField = new JTextField(30);
            javaExeField = new JTextField(30);
            workDirField = new JTextField(30);
            argsArea = new JTextArea(5, 30);
            argsArea.setLineWrap(true);
            
            // 设置默认值
            javaExeField.setText("C:\\source\\develop\\java\\jdk-17.0.4\\bin\\javaw.exe");
        }

        private void setupLayout() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            // 服务名称
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("服务名称:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(nameField, gbc);
            
            // Java路径
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("Java路径:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(javaExeField, gbc);
            gbc.gridx = 2;
            JButton browseJavaBtn = createStyledButton("浏览...");
            browseJavaBtn.addActionListener(e -> browseFile(javaExeField, JFileChooser.FILES_ONLY));
            formPanel.add(browseJavaBtn, gbc);
            
            // 工作目录
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("工作目录:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(workDirField, gbc);
            gbc.gridx = 2;
            JButton browseDirBtn = createStyledButton("浏览...");
            browseDirBtn.addActionListener(e -> browseFile(workDirField, JFileChooser.DIRECTORIES_ONLY));
            formPanel.add(browseDirBtn, gbc);
            
            // 启动参数
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("启动参数:"), gbc);
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(new JScrollPane(argsArea), gbc);
            
            add(formPanel, BorderLayout.CENTER);
            
            // 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = createStyledButton("确定");
            okBtn.addActionListener(e -> {
                if (validateInput()) {
                    confirmed = true;
                    config = new ServiceConfig(
                        nameField.getText().trim(),
                        javaExeField.getText().trim(),
                        workDirField.getText().trim(),
                        argsArea.getText().trim(),
                        "", // outLog - 不再使用
                        ""  // errLog - 不再使用
                    );
                    dispose();
                }
            });
            JButton cancelBtn = createStyledButton("取消");
            cancelBtn.addActionListener(e -> dispose());
            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);
            add(buttonPanel, BorderLayout.SOUTH);
            
            ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        private boolean validateInput() {
            if (nameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入服务名称", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (javaExeField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入Java路径", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (workDirField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入工作目录", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }

        private void browseFile(JTextField field, int mode) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(mode);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public ServiceConfig getConfig() {
            return config;
        }
    }

    // 编辑服务对话框
    private class EditServiceDialog extends JDialog {
        private boolean confirmed = false;
        private ServiceConfig config;
        
        private JTextField nameField;
        private JTextField javaExeField;
        private JTextField workDirField;
        private JTextArea argsArea;

        public EditServiceDialog(JFrame parent, ServiceConfig existingConfig, int index) {
            super(parent, "编辑服务", true);
            setSize(600, 450);
            setLocationRelativeTo(parent);
            
            this.config = existingConfig;
            
            initComponents();
            setupLayout();
        }

        private void initComponents() {
            nameField = new JTextField(30);
            javaExeField = new JTextField(30);
            workDirField = new JTextField(30);
            argsArea = new JTextArea(5, 30);
            argsArea.setLineWrap(true);
            
            // 填充现有配置
            if (config != null) {
                nameField.setText(config.getName() != null ? config.getName() : "");
                javaExeField.setText(config.getJavaExe() != null ? config.getJavaExe() : "");
                workDirField.setText(config.getWorkDir() != null ? config.getWorkDir() : "");
                argsArea.setText(config.getArgs() != null ? config.getArgs() : "");
            }
        }

        private void setupLayout() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            // 服务名称
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("服务名称:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(nameField, gbc);
            
            // Java路径
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("Java路径:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(javaExeField, gbc);
            gbc.gridx = 2;
            JButton browseJavaBtn = createStyledButton("浏览...");
            browseJavaBtn.addActionListener(e -> browseFile(javaExeField, JFileChooser.FILES_ONLY));
            formPanel.add(browseJavaBtn, gbc);
            
            // 工作目录
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("工作目录:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(workDirField, gbc);
            gbc.gridx = 2;
            JButton browseDirBtn = createStyledButton("浏览...");
            browseDirBtn.addActionListener(e -> browseFile(workDirField, JFileChooser.DIRECTORIES_ONLY));
            formPanel.add(browseDirBtn, gbc);
            
            // 启动参数
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            formPanel.add(new JLabel("启动参数:"), gbc);
            gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
            formPanel.add(new JScrollPane(argsArea), gbc);
            
            add(formPanel, BorderLayout.CENTER);
            
            // 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = createStyledButton("确定");
            okBtn.addActionListener(e -> {
                if (validateInput()) {
                    confirmed = true;
                    config = new ServiceConfig(
                        nameField.getText().trim(),
                        javaExeField.getText().trim(),
                        workDirField.getText().trim(),
                        argsArea.getText().trim(),
                        "", // outLog - 不再使用
                        ""  // errLog - 不再使用
                    );
                    dispose();
                }
            });
            JButton cancelBtn = createStyledButton("取消");
            cancelBtn.addActionListener(e -> dispose());
            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);
            add(buttonPanel, BorderLayout.SOUTH);
            
            ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        private boolean validateInput() {
            if (nameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入服务名称", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (javaExeField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入Java路径", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (workDirField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入工作目录", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }

        private void browseFile(JTextField field, int mode) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(mode);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public ServiceConfig getConfig() {
            return config;
        }
    }

    // 设置对话框
    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, appSettings);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            AppSettings newSettings = dialog.getSettings();
            if (newSettings != null) {
                appSettings = newSettings;
                try {
                    SettingsManager.saveSettings(appSettings);
                    appendLog("设置已保存");
                } catch (Exception e) {
                    appendLog("保存设置失败: " + e.getMessage());
                }
            }
        }
    }

    // 设置对话框
    private class SettingsDialog extends JDialog {
        private boolean confirmed = false;
        private AppSettings settings;
        
        private JTextField logBasePathField;

        public SettingsDialog(JFrame parent, AppSettings existingSettings) {
            super(parent, "设置", true);
            setSize(500, 200);
            setLocationRelativeTo(parent);
            
            this.settings = existingSettings;
            
            initComponents();
            setupLayout();
        }

        private void initComponents() {
            logBasePathField = new JTextField(40);
            if (settings != null) {
                logBasePathField.setText(settings.getLogBasePath());
            }
        }

        private void setupLayout() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.anchor = GridBagConstraints.WEST;
            
            // 日志基础路径
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("日志基础路径:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            formPanel.add(logBasePathField, gbc);
            gbc.gridx = 2;
            JButton browseBtn = createStyledButton("浏览...");
            browseBtn.addActionListener(e -> browseFile(logBasePathField, JFileChooser.DIRECTORIES_ONLY));
            formPanel.add(browseBtn, gbc);
            
            // 提示信息
            gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel hintLabel = new JLabel("<html><small>提示：每个服务的日志将保存在 日志路径/服务名称/ 目录下</small></html>");
            hintLabel.setForeground(Color.GRAY);
            formPanel.add(hintLabel, gbc);
            
            add(formPanel, BorderLayout.CENTER);
            
            // 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = createStyledButton("确定");
            okBtn.addActionListener(e -> {
                if (validateInput()) {
                    confirmed = true;
                    settings = new AppSettings(logBasePathField.getText().trim());
                    dispose();
                }
            });
            JButton cancelBtn = createStyledButton("取消");
            cancelBtn.addActionListener(e -> dispose());
            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);
            add(buttonPanel, BorderLayout.SOUTH);
            
            ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        private boolean validateInput() {
            if (logBasePathField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入日志基础路径", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }

        private void browseFile(JTextField field, int mode) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(mode);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public AppSettings getSettings() {
            return settings;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            new ServiceMonitorFrame().setVisible(true);
        });
    }
}
