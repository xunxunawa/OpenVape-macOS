package gg.vape.mac.injector;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal native-decorated window; percentages are stage markers, not an ETA. */
public final class CompactInjector {
    public static void main(String[] args) throws Exception { Injector.main(args); }
    private static final Color BG=new Color(20,23,27), PANEL=new Color(30,35,40), INK=new Color(233,241,243), ACCENT=new Color(42,212,172);
    private JFrame window;
    private final JComboBox<Injector.Target> games=new JComboBox<>(){public void paint(Graphics g){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);q.setColor(PANEL);q.fillRoundRect(0,0,getWidth(),getHeight(),16,16);q.setColor(new Color(55,66,72));q.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);Injector.Target t=(Injector.Target)getSelectedItem();String text=t==null?"未检测到游戏":"Lunar · "+t.version+" · "+t.pid;q.setFont(getFont());FontMetrics fm=q.getFontMetrics();int available=getWidth()-56;while(text.length()>1&&fm.stringWidth(text)>available)text=text.substring(0,text.length()-2)+"…";q.setColor(INK);q.drawString(text,14,(getHeight()-fm.getHeight())/2+fm.getAscent());q.setColor(ACCENT);int x=getWidth()-24,y=getHeight()/2;q.drawLine(x-3,y-2,x,y+1);q.drawLine(x,y+1,x+3,y-2);q.dispose();}};
    private final JButton importConfig=new JButton("导入配置"){protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);q.setColor(getBackground());q.fillRoundRect(0,0,getWidth(),getHeight(),16,16);q.dispose();super.paintComponent(g);}};
    private final JButton inject=new JButton("注入"){protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);q.setColor(getBackground());q.fillRoundRect(0,0,getWidth(),getHeight(),16,16);q.dispose();super.paintComponent(g);}};
    private final JProgressBar progress=new JProgressBar(0,100){protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);q.setColor(getBackground());q.fillRoundRect(0,0,getWidth(),getHeight(),getHeight(),getHeight());q.setColor(getForeground());int width=(int)Math.round(getWidth()*getPercentComplete());if(width>0)q.fillRoundRect(0,0,width,getHeight(),getHeight(),getHeight());q.dispose();}};
    private final JLabel code=new JLabel(" ",SwingConstants.CENTER);
    private final JButton restore=new JButton("恢复");
    private final JPanel actionArea=new JPanel(new GridBagLayout());
    private javax.swing.Timer morph;
    private final AtomicReference<Path> report=new AtomicReference<>();
    private boolean busy, importing, scanning, scanAvailable, completed, workerDone;
    private long started,lastTime,lastScan;
    private int heartbeats;
    private String operation="inject";
    static void show(){new CompactInjector().open();}
    private void open(){
        window=new JFrame("OpenVape");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);window.setResizable(false);
        JPanel content=buildContent();
        window.setContentPane(content);window.setSize(520,340);window.setLocationRelativeTo(null);window.setVisible(true);
        javax.swing.Timer timer=new javax.swing.Timer(350,e->{if(!window.isDisplayable()){((javax.swing.Timer)e.getSource()).stop();return;}if(busy)poll();else if(!importing)scan();});timer.start();scan();
    }
    private JPanel buildContent(){
        JPanel content=new JPanel();content.setBackground(BG);
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));content.setBorder(BorderFactory.createEmptyBorder(44,64,32,64));
        JLabel brand=new JLabel("OPENVAPE");brand.setForeground(INK);Font title=new Font("Helvetica Neue",Font.BOLD,30);
        try{for(Font f:Font.createFonts(new java.io.File("/System/Library/Fonts/HelveticaNeue.ttc")))if(f.getFontName().equals("Helvetica Neue Bold")){title=f.deriveFont(30f);break;}}catch(Exception unavailable){}
        java.util.Map<java.awt.font.TextAttribute,Object> attributes=new java.util.HashMap<>();attributes.put(java.awt.font.TextAttribute.TRACKING,.06f);brand.setFont(title.deriveFont(attributes));brand.setAlignmentX(.5f);
        games.setOpaque(true);games.setBackground(PANEL);games.setForeground(INK);games.setBorder(new RoundedBorder(new Color(55,66,72)));games.setMaximumSize(new Dimension(240,40));games.setPreferredSize(new Dimension(240,40));
        games.setUI(new BasicComboBoxUI(){protected JButton createArrowButton(){JButton b=new JButton("⌄");b.setBackground(PANEL);b.setForeground(ACCENT);b.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));return b;}});
        games.addActionListener(e->{if(!busy&&!importing&&completed){completed=false;code.setText(" ");actionArea.removeAll();actionArea.add(inject);actionArea.revalidate();actionArea.repaint();}});
        games.setRenderer((list,value,index,selected,focus)->{JLabel l=new JLabel(value==null?"未检测到游戏":("Lunar · "+value.version+" · "+value.pid));l.setOpaque(true);l.setBackground(selected?new Color(40,66,64):PANEL);l.setForeground(INK);l.setFont(games.getFont());l.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));return l;});
        importConfig.setMaximumSize(new Dimension(88,40));importConfig.setPreferredSize(new Dimension(88,40));importConfig.setBackground(ACCENT);importConfig.setForeground(BG);importConfig.setOpaque(false);importConfig.setContentAreaFilled(false);importConfig.setBorder(new RoundedBorder(new Color(55,66,72)));importConfig.setFocusPainted(false);importConfig.setUI(new javax.swing.plaf.basic.BasicButtonUI(){protected void paintText(Graphics g,JComponent c,Rectangle rect,String text){g.setColor(c.isEnabled()?BG:INK);FontMetrics fm=g.getFontMetrics();g.drawString(text,rect.x+(rect.width-fm.stringWidth(text))/2,rect.y+(rect.height-fm.getHeight())/2+fm.getAscent());}});importConfig.addActionListener(e->chooseConfiguration());
        inject.setAlignmentX(.5f);inject.setMaximumSize(new Dimension(200,36));inject.setPreferredSize(new Dimension(200,36));inject.setBackground(ACCENT);inject.setForeground(BG);inject.setOpaque(false);inject.setContentAreaFilled(false);inject.setBorder(new RoundedBorder(new Color(55,66,72)));inject.setFocusPainted(false);
        inject.setEnabled(false);inject.setBackground(PANEL);inject.setUI(new javax.swing.plaf.basic.BasicButtonUI(){protected void paintText(Graphics g,JComponent c,Rectangle rect,String text){g.setColor(c.isEnabled()?BG:INK);g.drawString(text,rect.x,rect.y+g.getFontMetrics().getAscent());}});inject.addActionListener(e->begin());
        progress.setUI(new BasicProgressBarUI());progress.setOpaque(false);progress.setBackground(new Color(48,61,66));progress.setForeground(ACCENT);progress.setBorderPainted(false);progress.setStringPainted(false);progress.setFont(UIManager.getFont("Label.font").deriveFont(10f));progress.setMaximumSize(new Dimension(180,5));progress.setPreferredSize(new Dimension(180,5));progress.setAlignmentX(.5f);
        code.setForeground(new Color(255,112,124));code.setAlignmentX(.5f);code.setFont(UIManager.getFont("Label.font").deriveFont(12f));
        content.add(brand);content.add(Box.createVerticalStrut(32));JPanel gameRow=new JPanel(new BorderLayout(12,0));gameRow.setOpaque(false);gameRow.setMaximumSize(new Dimension(340,40));gameRow.setPreferredSize(new Dimension(340,40));gameRow.add(games,BorderLayout.CENTER);gameRow.add(importConfig,BorderLayout.EAST);content.add(gameRow);content.add(Box.createVerticalStrut(15));actionArea.setOpaque(false);actionArea.setMaximumSize(new Dimension(340,36));actionArea.setPreferredSize(new Dimension(340,36));actionArea.add(inject);
        restore.setForeground(INK);restore.setBackground(PANEL);restore.setFocusPainted(false);restore.setBorder(new RoundedBorder(new Color(55,66,72)));restore.setPreferredSize(new Dimension(56,30));restore.setEnabled(false);
        restore.setToolTipText("停用并恢复本工具修改；已加载的 Java 类保留到游戏退出");restore.addActionListener(e->begin("stop"));
        JPanel row=new JPanel(new BorderLayout(12,0));row.setOpaque(false);row.setMaximumSize(new Dimension(340,36));row.setPreferredSize(new Dimension(340,36));row.add(actionArea,BorderLayout.CENTER);row.add(restore,BorderLayout.EAST);
        content.add(row);content.add(Box.createVerticalStrut(14));content.add(code);
        return content;
    }
    private static final class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final Color color;RoundedBorder(Color color){this.color=color;}
        public Insets getBorderInsets(Component c){return new Insets(6,10,6,10);}
        public void paintBorder(Component c,Graphics g,int x,int y,int width,int height){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);q.setColor(color);q.drawRoundRect(x,y,width-1,height-1,16,16);q.dispose();}
    }
    private void scan(){
        if(scanning||busy||importing||System.currentTimeMillis()-lastScan<2000)return;scanning=true;lastScan=System.currentTimeMillis();updateIdleButtons();
        Thread t=new Thread(()->{try{java.util.List<Injector.Target> found=Injector.targets();SwingUtilities.invokeLater(()->{if(busy||importing)return;Injector.Target chosen=(Injector.Target)games.getSelectedItem();long pid=chosen==null?-1:chosen.pid;
            boolean same=games.getItemCount()==found.size();for(int i=0;same&&i<found.size();i++)same=games.getItemAt(i).pid==found.get(i).pid;
            if(!same){games.removeAllItems();for(Injector.Target target:found){games.addItem(target);if(target.pid==pid)games.setSelectedItem(target);}}
            scanAvailable=true;if("Failed code E07".equals(code.getText()))code.setText(" ");
        });}catch(Exception failure){SwingUtilities.invokeLater(()->{if(!busy&&!importing){scanAvailable=false;code.setText("Failed code E07");updateIdleButtons();}});}finally{SwingUtilities.invokeLater(()->{scanning=false;updateIdleButtons();});}},"openvape-detect");t.setDaemon(true);t.start();
    }
    private void chooseConfiguration(){
        if(busy||importing||scanning)return;importing=true;updateIdleButtons();
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("导入配置");chooser.setFileFilter(new FileNameExtensionFilter("JSON 配置 (*.json)","json"));chooser.setAcceptAllFileFilterUsed(false);
        if(chooser.showOpenDialog(window)!=JFileChooser.APPROVE_OPTION){importing=false;updateIdleButtons();return;}
        Path source=chooser.getSelectedFile().toPath();code.setText(" ");code.setForeground(new Color(255,112,124));actionArea.removeAll();JLabel status=new JLabel("正在导入配置…",SwingConstants.CENTER);status.setForeground(INK);actionArea.add(status);actionArea.revalidate();actionArea.repaint();
        Thread t=new Thread(()->{try{Injector.stageConfigurationImport(source);SwingUtilities.invokeLater(()->finishConfigurationImport(null));}catch(Throwable failure){try{Injector.writeDiagnostic("","import-config",failure,message->{});}catch(Throwable ignored){}SwingUtilities.invokeLater(()->finishConfigurationImport(failure));}},"openvape-import-config");t.setDaemon(true);t.start();
    }
    private void finishConfigurationImport(Throwable failure){
        importing=false;actionArea.removeAll();actionArea.add(inject);actionArea.revalidate();actionArea.repaint();
        if(failure==null){code.setForeground(INK);code.setText("配置已导入；重启游戏后注入生效");}
        else{code.setForeground(new Color(255,112,124));code.setText("Failed code E11");}
        updateIdleButtons();
    }
    private void updateIdleButtons(){
        boolean gameActionsEnabled=!busy&&!importing&&scanAvailable&&games.getSelectedItem()!=null;
        games.setEnabled(!busy&&!importing);inject.setEnabled(gameActionsEnabled);restore.setEnabled(gameActionsEnabled);importConfig.setEnabled(!busy&&!importing&&!scanning);inject.setBackground(gameActionsEnabled?ACCENT:PANEL);
    }
    private void begin(){begin("inject");}
    private void begin(String action){
        Injector.Target target=(Injector.Target)games.getSelectedItem();if(busy||importing)return;if(target==null){code.setText("Failed code E01");return;}if(target.gameDirectory==null||!Files.isDirectory(target.gameDirectory)){code.setText("Failed code E02");return;}
        operation=action;busy=true;completed=false;workerDone=false;heartbeats=0;lastTime=0;started=System.currentTimeMillis();report.set(null);code.setText(" ");progress.setForeground(ACCENT);progress.setValue(10);games.setEnabled(false);inject.setEnabled(false);restore.setEnabled(false);importConfig.setEnabled(false);morphToProgress();
        Thread t=new Thread(()->{try{Path p=Injector.execute(action,Long.toString(target.pid),target.gameDirectory,message->{if(message.startsWith("本次报告："))report.set(Paths.get(message.substring("本次报告：".length())));if(message.startsWith("连接 PID"))SwingUtilities.invokeLater(()->{if(!completed)progress.setValue(30);});});report.set(p);
        }catch(Exception failure){Injector.writeDiagnostic(Long.toString(target.pid),action,failure,message->{});SwingUtilities.invokeLater(()->fail(errorCode(failure)));}finally{SwingUtilities.invokeLater(()->{workerDone=true;if(completed)unlock();});}},"openvape-inject");t.setDaemon(true);t.start();
    }
    private void poll(){
        if(completed){if(workerDone)unlock();return;}
        Injector.Target target=(Injector.Target)games.getSelectedItem();try{if(target!=null&&!ProcessHandle.of(target.pid).map(ProcessHandle::isAlive).orElse(false)){fail("E06");return;}}catch(SecurityException unavailable){}
        Path p=report.get();
        try{if(p!=null){Properties state=Injector.read(p);String s=state.getProperty("state","");long time=0;try{time=Long.parseLong(state.getProperty("time","0"));}catch(NumberFormatException ignored){}
            if(Set.of("FAILED","RUNTIME_FAILED").contains(s)){fail("E05");return;}
            if("stop".equals(operation)&&"STOPPED".equals(s)){completed=true;progress.setValue(100);code.setForeground(INK);code.setText("已停用并恢复；Java 类保留到游戏退出");if(workerDone)unlock();return;}
            if("SCHEDULED".equals(s))progress.setValue(65);if("READY".equals(s))progress.setValue(85);
            if("inject".equals(operation)&&Set.of("FRAME_OK","TICK_OK","RENDER_OK","ALREADY_ACTIVE").contains(s)){progress.setValue(95);if(time>0&&System.currentTimeMillis()-time<20000&&time!=lastTime){lastTime=time;heartbeats++;}if(heartbeats>=2){completed=true;progress.setValue(100);code.setForeground(INK);code.setText("新配置请在游戏内保存；关闭注入器不会自动保存");if(workerDone)unlock();return;}}
        }}catch(Exception failure){fail("E08");return;}
        if(System.currentTimeMillis()-started>45000)fail("E04");
    }
    private void fail(String value){completed=true;code.setForeground(new Color(255,112,124));code.setText("Failed code "+value);progress.setForeground(new Color(255,112,124));if(workerDone)unlock();}
    private void unlock(){busy=false;updateIdleButtons();if("stop".equals(operation)||code.getText().startsWith("Failed code ")){if(morph!=null)morph.stop();actionArea.removeAll();actionArea.add(inject);actionArea.revalidate();actionArea.repaint();}}
    private void morphToProgress(){
        if(morph!=null)morph.stop();actionArea.removeAll();progress.setPreferredSize(new Dimension(200,36));actionArea.add(progress);actionArea.revalidate();actionArea.repaint();long began=System.nanoTime();
        morph=new javax.swing.Timer(16,e->{double fraction=Math.min(1,(System.nanoTime()-began)/220_000_000.0);double eased=1-Math.pow(1-fraction,3);progress.setPreferredSize(new Dimension((int)Math.round(200-20*eased),(int)Math.round(36-31*eased)));actionArea.revalidate();actionArea.repaint();if(fraction>=1)((javax.swing.Timer)e.getSource()).stop();});morph.start();
    }
    static String errorCode(Throwable failure){
        if("IMPORT_REQUIRES_RESTART".equals(failure.getMessage()))return "E10";
        if(failure instanceof com.sun.tools.attach.AttachNotSupportedException)return "E03";
        if(failure instanceof java.util.concurrent.TimeoutException)return "E04";
        if(failure instanceof IllegalArgumentException)return "E09";
        if(failure instanceof java.io.IOException)return "E08";
        return "E05";
    }
}
