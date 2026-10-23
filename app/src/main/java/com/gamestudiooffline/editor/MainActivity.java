package com.gamestudiooffline.editor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class MainActivity extends Activity {
    int bg=Color.rgb(13,20,34), panel=Color.rgb(25,37,58), accent=Color.rgb(48,115,243);
    int fg=Color.rgb(239,244,255), muted=Color.rgb(160,178,204);
    JSONObject project; File activeFile; String tab="2D"; boolean preview=false; EditText nameBox;
    static final int EXPORT_ZIP=7, IMPORT_PROJECT=8, IMPORT_IMAGE=9;
    final Map<String,Bitmap> spriteCache=new HashMap<>();

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);home();}
    File dir(){File f=new File(getFilesDir(),"projects");f.mkdirs();return f;}
    File assetDir(){File f=new File(new File(getFilesDir(),"assets"),safe(project.optString("name","Game")));f.mkdirs();return f;}
    Bitmap loadSprite(String name){
        if(name==null||name.isEmpty())return null;
        Bitmap cached=spriteCache.get(name);if(cached!=null&&!cached.isRecycled())return cached;
        File file=new File(assetDir(),name);if(!file.isFile())return null;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();int max=Math.max(bounds.outWidth,bounds.outHeight);opts.inSampleSize=max>2048?4:(max>1024?2:1);
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);if(bitmap!=null)spriteCache.put(name,bitmap);return bitmap;
        }catch(Exception e){return null;}
    }
    void importImage(){
        if(objects().length()==0){toast("Hãy thêm một đối tượng trước");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
        try{startActivityForResult(i,IMPORT_IMAGE);}catch(Exception e){toast("Không mở được thư viện ảnh");}
    }
    void assignImage(Uri uri)throws Exception{
        JSONArray a=objects();if(a.length()==0)return;selected=Math.max(0,Math.min(selected,a.length()-1));JSONObject o=a.optJSONObject(selected);
        String mime=getContentResolver().getType(uri);String ext=mime!=null&&mime.contains("png")?"png":(mime!=null&&mime.contains("webp")?"webp":"jpg");
        String name="sprite_"+System.currentTimeMillis()+"."+ext;File dest=new File(assetDir(),name);
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){
            if(in==null)throw new IOException("Không đọc được ảnh");byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
        }
        o.put("image",name);spriteCache.remove(name);save();editor();toast("Đã gán ảnh cho "+o.optString("name"));
    }
    byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toByteArray();}
    void importProjectBytes(byte[] bytes)throws Exception{
        if(bytes.length>=2&&bytes[0]=='P'&&bytes[1]=='K'){
            JSONObject loaded=null;Map<String,byte[]> importedAssets=new HashMap<>();
            try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(bytes))){
                ZipEntry entry;while((entry=zin.getNextEntry())!=null){String name=entry.getName();byte[] content=readAll(zin);
                    if(name.equals("project.gsproj")||name.endsWith(".gsproj"))loaded=new JSONObject(new String(content,StandardCharsets.UTF_8));
                    else if(name.startsWith("assets/")&&!name.contains("..")){String base=name.substring("assets/".length());if(!base.isEmpty()&&!base.contains("/"))importedAssets.put(base,content);}
                    zin.closeEntry();
                }
            }
            if(loaded==null)throw new IOException("ZIP không có project.gsproj");
            project=loaded;File ad=assetDir();for(Map.Entry<String,byte[]> entry:importedAssets.entrySet()){try(FileOutputStream out=new FileOutputStream(new File(ad,entry.getKey()))){out.write(entry.getValue());}}
        }else project=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        activeFile=new File(dir(),safe(project.optString("name","Imported"))+".gsproj");
        save();spriteCache.clear();tab=project.optString("dimension","2D");preview=false;selected=0;editor();toast("Đã nhập dự án và tài nguyên (nếu có)");
    }
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(12),dp(8),dp(12),dp(12));l.setBackgroundColor(bg);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(3),dp(5),dp(3),dp(5));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    Button btn(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(fg);b.setAllCaps(false);GradientDrawable d=new GradientDrawable();d.setColor(s.startsWith("+")||s.contains("Tạo")||s.equals("Lưu")||s.equals("Chạy")?accent:panel);d.setCornerRadius(dp(9));b.setBackground(d);b.setPadding(dp(8),0,dp(8),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(p);b.setOnClickListener(v->r.run());return b;}
    ScrollView scroll(View v){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(v);return s;}
    TextView card(String s){TextView t=label(s,14,fg,false);t.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable d=new GradientDrawable();d.setColor(panel);d.setCornerRadius(dp(10));t.setBackground(d);return t;}
    String safe(String s){return s.replaceAll("[^A-Za-z0-9_-]","_");}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    JSONObject obj(String n,String type,double x,double y,double w,double h,int c)throws Exception{JSONObject o=new JSONObject();o.put("name",n);o.put("type",type);o.put("x",x);o.put("y",y);o.put("w",w);o.put("h",h);o.put("color",c);return o;}
    JSONObject event(String trigger,String action)throws Exception{JSONObject e=new JSONObject();e.put("trigger",trigger);e.put("action",action);return e;}

    void home(){
        preview=false;LinearLayout root=col();
        root.addView(label("🎮 GameStudio Offline",25,fg,true));
        root.addView(label("Tạo game 2D và 3D • No-code • Ưu tiên offline",13,muted,false));
        root.addView(label("Dự án mới",18,fg,true));
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setHint("Tên dự án");nameBox.setHintTextColor(muted);nameBox.setTextColor(fg);nameBox.setBackgroundColor(panel);root.addView(nameBox,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout creation=row();creation.addView(btn("Tạo game 2D",()->create("2D")));creation.addView(btn("Tạo game 3D",()->create("3D")));root.addView(creation);
        root.addView(label("Dự án đã lưu trên máy",18,fg,true));
        File[] fs=dir().listFiles((f,n)->n.endsWith(".gsproj"));
        if(fs==null||fs.length==0)root.addView(card("Chưa có dự án. Tạo một dự án mới phía trên."));
        else for(File f:fs){try{JSONObject p=read(f);TextView t=card("📁 "+p.optString("name",f.getName())+" • "+p.optString("dimension","2D")+"\nChạm để mở");root.addView(t);t.setOnClickListener(v->open(f));}catch(Exception ignored){}}
        root.addView(btn("Nhập dự án JSON/ZIP",this::importJson));
        root.addView(label("Bản nền tảng: chỉnh sửa/lưu cảnh và khối sự kiện đang được xây dựng. Xuất APK của game người dùng là giai đoạn riêng.",12,muted,false));
        setContentView(scroll(root));
    }

    void create(String dimension){
        String n=nameBox.getText().toString().trim();if(n.isEmpty())n=dimension.equals("3D")?"My3DGame":"MyGame";
        try{
            project=new JSONObject();project.put("schema",1);project.put("name",n);project.put("dimension",dimension);project.put("width",960);project.put("height",540);
            JSONArray os=new JSONArray();
            if(dimension.equals("2D")){os.put(obj("Player","player",130,350,48,60,Color.rgb(40,132,255)));os.put(obj("Ground","platform",0,465,960,75,Color.rgb(56,174,99)));os.put(obj("Coin","coin",500,340,32,32,Color.YELLOW));}
            else{os.put(obj("Camera","camera",0,0,0,0,Color.WHITE));os.put(obj("Light","light",0,0,0,0,Color.YELLOW));os.put(obj("Cube","cube",430,260,90,90,Color.rgb(52,138,255)));os.put(obj("Cube 2","cube",580,330,65,65,Color.rgb(255,128,70)));}
            project.put("objects",os);JSONArray ev=new JSONArray();ev.put(event("Khi bắt đầu cảnh","Đặt biến Score = 0"));
            ev.put(event(dimension.equals("2D")?"Khi Player chạm Coin":"Khi bắt đầu cảnh",dimension.equals("2D")?"Tăng Score thêm 1 và ẩn Coin":"Đặt Camera nhìn vào Cube"));project.put("events",ev);
            activeFile=new File(dir(),safe(n)+".gsproj");tab=dimension;save();selected=0;editor();
        }catch(Exception e){toast("Lỗi tạo dự án: "+e.getMessage());}
    }
    int selected=0;
    final String[] triggerChoices={"Khi bắt đầu cảnh","Mỗi khung hình","Mỗi 1 giây","Mỗi 5 giây","Mỗi 10 giây","Khi Player chạm Coin","Khi Player chạm Enemy","Khi bấm nút Nhảy","Khi điều kiện chuyển thành đúng","Trong khi điều kiện đúng","Khi Player rơi khỏi màn hình"};
    final String[] actionChoices={"Đặt biến Score = 0","Tăng Score thêm 1","Tăng Score thêm 10","Giảm Score đi 1","Đặt biến Lives = 3","Tăng Lives thêm 1","Ẩn Coin","Hiện Coin","Ẩn Enemy","Hiện Enemy","Cho Player nhảy","Trừ 1 mạng","Di chuyển Player","Di chuyển Player sang trái","Di chuyển Player sang phải","Hồi sinh Player","Đặt Gravity = 950","Đặt MoveSpeed = 240","Đặt JumpStrength = 430","Lệnh tùy chỉnh (xem ô bên dưới)"};
    // Runtime state is separate from editor data; play-testing never changes saved object positions.
    final Map<String,Double> runtimeVars = new HashMap<>();
    final Set<String> hiddenRuntimeObjects = new HashSet<>();
    final Set<String> previousContacts = new HashSet<>();
    final Map<Integer,Long> eventTimerLastFired = new HashMap<>();
    final Map<Integer,Boolean> eventConditionWasTrue = new HashMap<>();
    double runtimePlayerX=120, runtimePlayerY=350, runtimePlayerVY=0;
    double runtimeSpawnX=120, runtimeSpawnY=350;
    double runtimeGravity=950, runtimeMoveSpeed=240, runtimeJumpStrength=430, runtimeTimeScale=1;
    float moveAxis=0;
    boolean runtimeGrounded=false, jumpRequested=false, runtimeStarted=false;
    int runtimeScore=0, runtimeLives=3;
    long lastRuntimeTick=0, runtimeStartMillis=0;

    void startRuntime(){
        runtimeVars.clear();runtimeVars.put("Score",0d);runtimeVars.put("Lives",3d);runtimeVars.put("Timer",0d);
        hiddenRuntimeObjects.clear();previousContacts.clear();eventTimerLastFired.clear();eventConditionWasTrue.clear();
        runtimeScore=0;runtimeLives=3;runtimeGravity=950;runtimeMoveSpeed=240;runtimeJumpStrength=430;runtimeTimeScale=1;
        runtimePlayerVY=0;moveAxis=0;jumpRequested=false;runtimeGrounded=false;
        JSONObject player=findPlayer();if(player!=null){runtimePlayerX=player.optDouble("x",120);runtimePlayerY=player.optDouble("y",350);}
        runtimeSpawnX=runtimePlayerX;runtimeSpawnY=runtimePlayerY;
        lastRuntimeTick=System.currentTimeMillis();runtimeStartMillis=lastRuntimeTick;runtimeStarted=true;
        executePhase("start",null);
    }
    void stopRuntime(){runtimeStarted=false;moveAxis=0;jumpRequested=false;hiddenRuntimeObjects.clear();previousContacts.clear();}
    JSONObject findPlayer(){
        JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&(o.optString("type").equalsIgnoreCase("player")||o.optString("name").equalsIgnoreCase("player")))return o;}return null;
    }
    void executePhase(String phase,String contactType){
        JSONArray events=project.optJSONArray("events");if(events==null)return;
        long now=System.currentTimeMillis();
        double elapsed=Math.max(0,(now-runtimeStartMillis)/1000d);
        runtimeVars.put("Timer",elapsed);
        for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            String trigger=e.optString("trigger","").toLowerCase(Locale.ROOT);
            String condition=e.optString("condition","").trim();
            boolean match=false;
            if("start".equals(phase))match=trigger.contains("bắt đầu cảnh");
            else if("frame".equals(phase)){
                if(trigger.contains("mỗi khung hình")||trigger.contains("mỗi frame"))match=true;
                else if(trigger.contains("trong khi điều kiện đúng"))match=!condition.isEmpty()&&evaluateCondition(condition);
                else if(trigger.contains("khi điều kiện chuyển thành đúng")){
                    boolean current=!condition.isEmpty()&&evaluateCondition(condition);
                    boolean wasTrue=eventConditionWasTrue.getOrDefault(i,false);
                    eventConditionWasTrue.put(i,current);match=current&&!wasTrue;
                }else{
                    Matcher timer=Pattern.compile("mỗi\\s+(\\d+(?:\\.\\d+)?)\\s+giây").matcher(trigger);
                    if(timer.find()){
                        double interval=Math.max(.1,Double.parseDouble(timer.group(1)));
                        long previous=eventTimerLastFired.getOrDefault(i,runtimeStartMillis);
                        if(now-previous>=interval*1000){match=true;eventTimerLastFired.put(i,now);}
                    }else if(trigger.contains("player rơi khỏi màn hình")){
                        boolean fell=runtimePlayerY>560;
                        boolean wasFallen=eventConditionWasTrue.getOrDefault(i,false);
                        eventConditionWasTrue.put(i,fell);match=fell&&!wasFallen;
                    }
                }
            }
            else if("jump".equals(phase))match=trigger.contains("bấm nút nhảy")||trigger.contains("nút nhảy");
            else if("collision".equals(phase))match=trigger.contains("chạm")&&contactType!=null&&trigger.contains(contactType.toLowerCase(Locale.ROOT));
            if(match&&!condition.isEmpty()&&!trigger.contains("điều kiện chuyển thành đúng")&&!trigger.contains("trong khi điều kiện đúng"))match=evaluateCondition(condition);
            if(match){
                String custom=e.optString("customAction","").trim();
                runAction(custom.isEmpty()?e.optString("action",""):custom);
            }
        }
    }
    boolean evaluateCondition(String expression){
        if(expression==null||expression.trim().isEmpty())return true;
        String s=expression.trim();
        while(s.startsWith("(")&&s.endsWith(")")&&isOuterParentheses(s))s=s.substring(1,s.length()-1).trim();
        int split=findTopLevelLogical(s,"||");
        if(split>=0)return evaluateCondition(s.substring(0,split))||evaluateCondition(s.substring(split+2));
        split=findTopLevelLogical(s,"&&");
        if(split>=0)return evaluateCondition(s.substring(0,split))&&evaluateCondition(s.substring(split+2));
        if(s.startsWith("!"))return !evaluateCondition(s.substring(1));
        Matcher comparison=Pattern.compile("^(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+)$").matcher(s);
        if(comparison.matches()){
            String left=comparison.group(1).trim(),op=comparison.group(2),right=comparison.group(3).trim();
            Double a=resolveValue(left),b=resolveValue(right);
            if(a==null||b==null)return "==".equals(op)?resolveText(left).equalsIgnoreCase(resolveText(right)):"!=".equals(op)&&!resolveText(left).equalsIgnoreCase(resolveText(right));
            switch(op){case ">=":return a>=b;case "<=":return a<=b;case ">":return a>b;case "<":return a<b;case "==":return Math.abs(a-b)<0.000001;case "!=":return Math.abs(a-b)>=0.000001;}
        }
        if("true".equalsIgnoreCase(s))return true;if("false".equalsIgnoreCase(s))return false;
        Double value=resolveValue(s);return value!=null&&value!=0;
    }
    int findTopLevelLogical(String s,String operator){
        int depth=0;
        for(int i=0;i<=s.length()-operator.length();i++){
            char ch=s.charAt(i);if(ch=='(')depth++;else if(ch==')')depth--;
            if(depth==0&&s.startsWith(operator,i))return i;
        }
        return -1;
    }
    boolean isOuterParentheses(String s){
        int depth=0;for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(ch=='(')depth++;else if(ch==')')depth--;if(depth==0&&i<s.length()-1)return false;}return depth==0;
    }
    String resolveText(String token){
        String key=token.trim();
        if(key.equalsIgnoreCase("Player.X"))return Double.toString(runtimePlayerX);
        if(key.equalsIgnoreCase("Player.Y"))return Double.toString(runtimePlayerY);
        if(key.equalsIgnoreCase("Timer"))return Double.toString(Math.max(0,(System.currentTimeMillis()-runtimeStartMillis)/1000d));
        return key;
    }
    Double resolveValue(String token){
        String s=token.trim();
        try{return Double.parseDouble(s);}catch(Exception ignored){}
        if("Player.X".equalsIgnoreCase(s))return runtimePlayerX;
        if("Player.Y".equalsIgnoreCase(s))return runtimePlayerY;
        if("Player.VY".equalsIgnoreCase(s))return runtimePlayerVY;
        if("Timer".equalsIgnoreCase(s))return Math.max(0,(System.currentTimeMillis()-runtimeStartMillis)/1000d);
        for(Map.Entry<String,Double> entry:runtimeVars.entrySet())if(entry.getKey().equalsIgnoreCase(s))return entry.getValue();
        return null;
    }
    void putRuntimeVar(String name,double value){
        String key=name.trim();for(String existing:runtimeVars.keySet())if(existing.equalsIgnoreCase(key)){key=existing;break;}runtimeVars.put(key,value);
    }
    void updateHudValues(){
        runtimeScore=(int)Math.round(runtimeVars.getOrDefault("Score",0d));
        runtimeLives=(int)Math.round(runtimeVars.getOrDefault("Lives",3d));
    }
    void hideMatchingObjects(String query,boolean hidden){
        String q=query.trim().toLowerCase(Locale.ROOT);JSONArray a=objects();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String name=o.optString("name","").toLowerCase(Locale.ROOT),type=o.optString("type","").toLowerCase(Locale.ROOT);
            if(name.equals(q)||type.equals(q)||name.contains(q)){if(hidden)hiddenRuntimeObjects.add(o.optString("name",""));else hiddenRuntimeObjects.remove(o.optString("name",""));}
        }
    }
    void runAction(String action){
        String low=action.toLowerCase(Locale.ROOT);
        String command=action.trim();
        Matcher customSet=Pattern.compile("(?i)^set\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher customAdd=Pattern.compile("(?i)^add\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(-?\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher customSub=Pattern.compile("(?i)^subtract\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(-?\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher teleport=Pattern.compile("(?i)^teleport\\s+player\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher hide=Pattern.compile("(?i)^(hide|show|destroy)\\s+(.+)$").matcher(command);
        if(customSet.matches()){
            String key=customSet.group(1);double value=Double.parseDouble(customSet.group(2));
            if(key.equalsIgnoreCase("Player.X"))runtimePlayerX=value;
            else if(key.equalsIgnoreCase("Player.Y"))runtimePlayerY=value;
            else if(key.equalsIgnoreCase("Player.VY"))runtimePlayerVY=value;
            else if(key.equalsIgnoreCase("Gravity"))runtimeGravity=Math.max(0,value);
            else if(key.equalsIgnoreCase("MoveSpeed"))runtimeMoveSpeed=Math.max(0,value);
            else if(key.equalsIgnoreCase("JumpStrength"))runtimeJumpStrength=Math.max(0,value);
            else if(key.equalsIgnoreCase("TimeScale"))runtimeTimeScale=Math.max(0,Math.min(3,value));
            else putRuntimeVar(key,value);
            updateHudValues();return;
        }
        if(customAdd.matches()||customSub.matches()){
            Matcher m=customAdd.matches()?customAdd:customSub;String key=m.group(1);double value=Double.parseDouble(m.group(2));
            putRuntimeVar(key,resolveValue(key)==null?0:resolveValue(key)+(m==customAdd?value:-value));updateHudValues();return;
        }
        if(teleport.matches()){runtimePlayerX=Double.parseDouble(teleport.group(1));runtimePlayerY=Double.parseDouble(teleport.group(2));runtimePlayerVY=0;updateHudValues();return;}
        if(hide.matches()){String verb=hide.group(1).toLowerCase(Locale.ROOT),target=hide.group(2);hideMatchingObjects(target,!verb.equals("show"));updateHudValues();return;}
        if(command.equalsIgnoreCase("respawn Player")||command.equalsIgnoreCase("respawn player")){runtimePlayerX=runtimeSpawnX;runtimePlayerY=runtimeSpawnY;runtimePlayerVY=0;runtimeGrounded=false;updateHudValues();return;}
        Matcher set=Pattern.compile("(?i).*đặt(?:\\s+biến)?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?\\d+(?:\\.\\d+)?).*").matcher(action);
        if(set.matches()){try{putRuntimeVar(set.group(1),Double.parseDouble(set.group(2)));}catch(Exception ignored){}}
        Matcher inc=Pattern.compile("(?i).*tăng\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+thêm\\s+(-?\\d+(?:\\.\\d+)?).*").matcher(action);
        Matcher dec=Pattern.compile("(?i).*giảm\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+đi\\s+(-?\\d+(?:\\.\\d+)?).*").matcher(action);
        if(inc.matches()){try{String key=inc.group(1);putRuntimeVar(key,(resolveValue(key)==null?0:resolveValue(key))+Double.parseDouble(inc.group(2)));}catch(Exception ignored){}}
        if(dec.matches()){try{String key=dec.group(1);putRuntimeVar(key,(resolveValue(key)==null?0:resolveValue(key))-Double.parseDouble(dec.group(2)));}catch(Exception ignored){}}
        if(low.contains("ẩn coin")||low.contains("hide coin"))setCoinsHidden(true);
        if(low.contains("hiện coin")||low.contains("show coin"))setCoinsHidden(false);
        if(low.contains("ẩn enemy"))hideMatchingObjects("enemy",true);
        if(low.contains("hiện enemy"))hideMatchingObjects("enemy",false);
        if(low.contains("trừ")&&low.contains("mạng"))putRuntimeVar("Lives",(resolveValue("Lives")==null?3:resolveValue("Lives"))-1);
        if(low.contains("hồi sinh player")){runtimePlayerX=runtimeSpawnX;runtimePlayerY=runtimeSpawnY;runtimePlayerVY=0;runtimeGrounded=false;}
        if(low.contains("gravity ="))runtimeGravity=950;
        if(low.contains("movespeed ="))runtimeMoveSpeed=240;
        if(low.contains("jumpstrength ="))runtimeJumpStrength=430;
        if(low.contains("nhảy")||low.contains("jump")){if(runtimeGrounded){runtimePlayerVY=-runtimeJumpStrength;runtimeGrounded=false;}}
        if(low.contains("di chuyển player")||low.contains("move player")){if(low.contains("sang trái")||low.contains("left"))runtimePlayerX-=3;else if(low.contains("sang phải")||low.contains("right"))runtimePlayerX+=3;else runtimePlayerX+=2;runtimePlayerX=Math.max(0,Math.min(900,runtimePlayerX));}
        updateHudValues();
    }
    void setCoinsHidden(boolean hidden){
        JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("type").equalsIgnoreCase("coin")){if(hidden)hiddenRuntimeObjects.add(o.optString("name"));else hiddenRuntimeObjects.remove(o.optString("name"));}}
    }
    void tickRuntime(){
        if(!runtimeStarted)startRuntime();
        long now=System.currentTimeMillis();double dt=Math.min(.05,Math.max(.001,(now-lastRuntimeTick)/1000d));lastRuntimeTick=now;
        JSONObject player=findPlayer();if(player==null)return;
        executePhase("frame",null);
        if(jumpRequested){executePhase("jump",null);if(runtimeGrounded){runtimePlayerVY=-runtimeJumpStrength;runtimeGrounded=false;}jumpRequested=false;}
        double pw=Math.max(20,player.optDouble("w",48)),ph=Math.max(20,player.optDouble("h",60));
        double oldBottom=runtimePlayerY+ph;
        dt*=runtimeTimeScale;
        runtimePlayerX=Math.max(0,Math.min(960-pw,runtimePlayerX+moveAxis*runtimeMoveSpeed*dt));
        runtimePlayerVY=Math.min(1200,runtimePlayerVY+runtimeGravity*dt);
        runtimePlayerY=Math.max(0,runtimePlayerY+runtimePlayerVY*dt);
        runtimeGrounded=false;
        JSONArray all=objects();Set<String> currentContacts=new HashSet<>();
        for(int i=0;i<all.length();i++){
            JSONObject o=all.optJSONObject(i);if(o==null||o==player)continue;
            String type=o.optString("type","").toLowerCase(Locale.ROOT),name=o.optString("name","Object");
            if(hiddenRuntimeObjects.contains(name))continue;
            double ox=o.optDouble("x"),oy=o.optDouble("y"),ow=Math.max(1,o.optDouble("w")),oh=Math.max(1,o.optDouble("h"));
            boolean overlap=runtimePlayerX<ox+ow&&runtimePlayerX+pw>ox&&runtimePlayerY<oy+oh&&runtimePlayerY+ph>oy;
            if(type.equals("platform")&&runtimePlayerVY>=0&&oldBottom<=oy+12&&runtimePlayerY+ph>=oy&&runtimePlayerX+pw>ox&&runtimePlayerX<ox+ow){runtimePlayerY=oy-ph;runtimePlayerVY=0;runtimeGrounded=true;}
            if(overlap&&(type.equals("coin")||type.equals("enemy"))){String key=type;currentContacts.add(key);if(!previousContacts.contains(key))executePhase("collision",key);}
        }
        if(runtimePlayerY+ph>=500){runtimePlayerY=500-ph;runtimePlayerVY=0;runtimeGrounded=true;}
        previousContacts.clear();previousContacts.addAll(currentContacts);
        runtimeScore=(int)Math.round(runtimeVars.getOrDefault("Score",0d));runtimeLives=(int)Math.round(runtimeVars.getOrDefault("Lives",3d));
    }
    Button holdButton(String text,int direction){
        Button b=btn(text,()->{});b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){moveAxis=direction;return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){if(moveAxis==direction)moveAxis=0;return true;}return true;});return b;
    }
    JSONObject read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);}return new JSONObject(b.toString("UTF-8"));}
    void open(File f){try{project=read(f);activeFile=f;tab=project.optString("dimension","2D");preview=false;selected=0;editor();}catch(Exception e){toast("Không mở được dự án");}}
    void save(){try(FileOutputStream out=new FileOutputStream(activeFile)){out.write(project.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception e){toast("Lỗi lưu");}}
    JSONArray objects(){JSONArray a=project.optJSONArray("objects");if(a==null){a=new JSONArray();try{project.put("objects",a);}catch(Exception ignored){}}return a;}
    void add(String n,String type,int color){try{JSONArray a=objects();a.put(obj(n+" "+(a.length()+1),type,220+(a.length()*47)%540,180+(a.length()*31)%230,type.equals("coin")?30:54,type.equals("coin")?30:54,color));selected=a.length()-1;save();editor();}catch(Exception ignored){}}

    void editor(){
        LinearLayout root=col();LinearLayout top=row();top.addView(btn("‹",this::home));
        top.addView(label(project.optString("name","Game"),16,fg,true),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(btn("Lưu",()->{save();toast("Đã lưu ngoại tuyến");}));
        top.addView(btn(preview?"Dừng":"Chạy",()->{if(!preview)startRuntime();else stopRuntime();preview=!preview;editor();}));root.addView(top);
        LinearLayout tabs=row();for(String t:new String[]{"2D","3D","Đối tượng","Sự kiện"})tabs.addView(btn(t,()->{tab=t;editor();}));root.addView(scroll(tabs));
        if(preview){
            boolean preview3D=project.optString("dimension","2D").equals("3D");
            root.addView(label("CHẠY THỬ • Sự kiện đang được thực thi",12,Color.GREEN,true));
            root.addView(new SceneView(true,preview3D),new LinearLayout.LayoutParams(-1,dp(330)));
            if(!preview3D){LinearLayout controls=row();controls.addView(holdButton("◀", -1));controls.addView(btn("NHẢY",()->{jumpRequested=true;}));controls.addView(holdButton("▶", 1));root.addView(controls);}
            root.addView(label("Điểm: "+runtimeScore+"     Mạng: "+runtimeLives,16,fg,true));
            root.addView(btn("Quay lại chỉnh sửa",()->{stopRuntime();preview=false;editor();}));
        }
        else if(tab.equals("2D")||tab.equals("3D")){
            boolean three=tab.equals("3D");root.addView(label(three?"Cảnh 3D • khối cơ bản":"Cảnh 2D • kéo đối tượng để di chuyển",13,muted,true));
            root.addView(new SceneView(false,three),new LinearLayout.LayoutParams(-1,dp(320)));
            LinearLayout tools=row();tools.addView(btn("+ Player",()->add("Player","player",Color.BLUE)));tools.addView(btn("+ Enemy",()->add("Enemy","enemy",Color.RED)));tools.addView(btn("+ Khối",()->add("Cube","cube",Color.rgb(110,130,255))));tools.addView(btn("+ Coin",()->add("Coin","coin",Color.YELLOW)));tools.addView(btn("+ Nền",()->add("Platform","platform",Color.GREEN)));root.addView(scroll(tools));
            root.addView(label("Đang chọn: "+selectedName(),14,fg,true));root.addView(btn("Sửa thuộc tính",this::editObject));root.addView(btn("Xuất dự án ZIP",this::exportZip));root.addView(btn("Xuất APK",this::apkNotice));
        }else if(tab.equals("Đối tượng"))objectPanel(root);else eventPanel(root);
        setContentView(scroll(root));
    }
    String selectedName(){JSONArray a=objects();if(a.length()==0)return "(không có)";selected=Math.max(0,Math.min(selected,a.length()-1));return a.optJSONObject(selected).optString("name","Object");}
    void objectPanel(LinearLayout root){
        root.addView(label("Đối tượng trong cảnh",18,fg,true));JSONArray a=objects();
        for(int i=0;i<a.length();i++){int idx=i;JSONObject o=a.optJSONObject(i);TextView t=card((i==selected?"● ":"○ ")+o.optString("name")+"  •  "+o.optString("type")+"\nX="+(int)o.optDouble("x")+"  Y="+(int)o.optDouble("y"));root.addView(t);t.setOnClickListener(v->{selected=idx;editor();});}
        root.addView(btn("+ Thêm đối tượng",()->new AlertDialog.Builder(this).setTitle("Loại đối tượng").setItems(new String[]{"Nhân vật","Enemy","Khối 3D","Coin","Nền sàn"},(d,w)->{if(w==0)add("Player","player",Color.BLUE);if(w==1)add("Enemy","enemy",Color.RED);if(w==2)add("Cube","cube",Color.rgb(110,130,255));if(w==3)add("Coin","coin",Color.YELLOW);if(w==4)add("Platform","platform",Color.GREEN);}).show()));
        root.addView(btn("Gán ảnh từ thư viện",this::importImage));
        root.addView(btn("Sửa thuộc tính",this::editObject));
        root.addView(btn("Nhân bản đối tượng",this::duplicateSelected));
        root.addView(btn("Xóa đối tượng",()->{if(objects().length()>0){objects().remove(selected);selected=Math.max(0,selected-1);save();editor();}}));
    }
    EditText field(String s){EditText e=new EditText(this);e.setSingleLine(true);e.setText(s);e.setTextColor(fg);e.setBackgroundColor(panel);return e;}
    void editObject(){
        JSONArray a=objects();if(a.length()==0)return;selected=Math.max(0,Math.min(selected,a.length()-1));JSONObject o=a.optJSONObject(selected);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(10),dp(6),dp(10),dp(6));box.setOrientation(1);
        EditText n=field(o.optString("name"));EditText x=field(""+(int)o.optDouble("x"));EditText y=field(""+(int)o.optDouble("y"));EditText w=field(""+(int)o.optDouble("w"));EditText h=field(""+(int)o.optDouble("h"));
        String[] types={"player","enemy","cube","coin","platform"};
        String[] typeLabels={"Nhân vật (Player)","Kẻ địch (Enemy)","Khối 3D (Cube)","Vật phẩm (Coin)","Nền sàn (Platform)"};
        Spinner type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,typeLabels));
        String currentType=o.optString("type","cube");
        for(int i=0;i<types.length;i++)if(types[i].equalsIgnoreCase(currentType)){type.setSelection(i);break;}
        String[] colorLabels={"Xanh dương","Đỏ","Tím","Vàng","Xanh lá","Trắng"};
        int[] colors={Color.rgb(40,116,240),Color.RED,Color.rgb(150,80,230),Color.YELLOW,Color.rgb(56,174,99),Color.WHITE};
        Spinner color=new Spinner(this);
        color.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,colorLabels));
        int oldColor=o.optInt("color",Color.BLUE), colorIndex=0;
        for(int i=0;i<colors.length;i++)if(colors[i]==oldColor){colorIndex=i;break;}
        color.setSelection(colorIndex);
        for(Object[] pair:new Object[][]{{"Tên",n},{"Loại đối tượng",type},{"Màu",color},{"Vị trí X",x},{"Vị trí Y",y},{"Chiều rộng",w},{"Chiều cao",h}}){box.addView(label((String)pair[0],12,muted,false));box.addView((View)pair[1]);}
        new AlertDialog.Builder(this).setTitle("Thuộc tính đối tượng").setView(box).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,z)->{try{o.put("name",n.getText().toString().trim().isEmpty()?currentType:n.getText().toString().trim());o.put("type",types[type.getSelectedItemPosition()]);o.put("color",colors[color.getSelectedItemPosition()]);o.put("x",num(x));o.put("y",num(y));o.put("w",Math.max(1,num(w)));o.put("h",Math.max(1,num(h)));spriteCache.clear();save();editor();}catch(Exception ex){toast("Không lưu được thuộc tính");}}).show();
    }
    void duplicateSelected(){
        JSONArray a=objects();if(a.length()==0)return;
        try{
            selected=Math.max(0,Math.min(selected,a.length()-1));
            JSONObject source=a.optJSONObject(selected);
            JSONObject copy=new JSONObject(source.toString());
            copy.put("name",source.optString("name","Object")+" copy");
            copy.put("x",Math.min(900,source.optDouble("x")+24));
            copy.put("y",Math.min(500,source.optDouble("y")+24));
            a.put(copy);selected=a.length()-1;save();editor();toast("Đã nhân bản đối tượng");
        }catch(Exception ex){toast("Không nhân bản được đối tượng");}
    }
    double num(EditText e){try{return Double.parseDouble(e.getText().toString());}catch(Exception ex){return 0;}}
    void eventPanel(LinearLayout root){
        root.addView(label("Khối sự kiện No-code",18,fg,true));
        root.addView(label("Chạm vào một khối để sửa điều kiện/hành động. Nhấn giữ để xóa.",12,muted,false));
        JSONArray a=project.optJSONArray("events");
        if(a!=null)for(int i=0;i<a.length();i++){
            int idx=i;JSONObject e=a.optJSONObject(i);
            String shownAction=e.optString("customAction","").trim();if(shownAction.isEmpty())shownAction=e.optString("action","");
            String cond=e.optString("condition","").trim();
            TextView t=card("🟩 KHI "+e.optString("trigger")+(cond.isEmpty()?"":"\\n🔎 NẾU "+cond)+"\\n🟦 LÀM "+shownAction);
            root.addView(t);t.setOnClickListener(v->editEvent(idx));
            t.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("Xóa khối này?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(d,w)->{project.optJSONArray("events").remove(idx);save();editor();}).show();return true;});
        }
        root.addView(btn("+ Thêm sự kiện",()->editEvent(-1)));
        root.addView(label("Điều kiện hỗ trợ: Score >= 10, Lives <= 0, Timer > 5, Player.X >= 300; kết hợp &&, ||, ! và dấu ngoặc. Chọn 'Khi điều kiện chuyển thành đúng' để chạy một lần khi điều kiện vừa đúng.",12,muted,false));
        root.addView(label("Lệnh tùy chỉnh: set Score = 10; add Score 5; subtract Lives 1; set Player.X = 100; set Player.Y = 200; set Gravity = 800; set MoveSpeed = 300; set JumpStrength = 500; set TimeScale = 0.5; teleport Player 120 300; hide Coin; show Enemy; destroy Enemy; respawn Player.",12,muted,false));
    }
    void editEvent(int index){
        JSONArray existingEvents=project.optJSONArray("events");
        if(existingEvents==null){existingEvents=new JSONArray();try{project.put("events",existingEvents);}catch(Exception ignored){}}
        final JSONArray list=existingEvents;
        JSONObject old=index>=0&&index<list.length()?list.optJSONObject(index):new JSONObject();
        Spinner trigger=new Spinner(this),action=new Spinner(this);
        trigger.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,triggerChoices));
        action.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,actionChoices));
        if(old!=null){for(int i=0;i<triggerChoices.length;i++)if(triggerChoices[i].equals(old.optString("trigger")))trigger.setSelection(i);for(int i=0;i<actionChoices.length;i++)if(actionChoices[i].equals(old.optString("action")))action.setSelection(i);}
        EditText condition=field(old==null?"":old.optString("condition",""));
        condition.setHint("VD: Score >= 10 && Lives > 0");
        EditText customAction=field(old==null?"":old.optString("customAction",""));
        customAction.setHint("Lệnh tùy chỉnh, để trống dùng lựa chọn phía trên");
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        box.addView(label("KHI — Thời điểm / sự kiện",13,muted,true));box.addView(trigger);
        box.addView(label("NẾU — Điều kiện bổ sung (có thể để trống)",13,muted,true));box.addView(condition);
        box.addView(label("LÀM — Hành động",13,muted,true));box.addView(action);
        box.addView(label("Lệnh tùy chỉnh (tùy chọn, thay thế hành động đã chọn)",13,muted,true));box.addView(customAction);
        TextView hint=label("Ví dụ điều kiện: Score >= 10 && Lives > 0. Ví dụ lệnh: add Score 5 hoặc teleport Player 100 250.",11,muted,false);box.addView(hint);
        new AlertDialog.Builder(this).setTitle(index<0?"Tạo sự kiện":"Sửa sự kiện").setView(scroll(box)).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{
            try{JSONObject e=new JSONObject();e.put("trigger",trigger.getSelectedItem().toString());e.put("action",action.getSelectedItem().toString());e.put("condition",condition.getText().toString().trim());e.put("customAction",customAction.getText().toString().trim());if(index>=0&&index<list.length())list.put(index,e);else list.put(e);save();editor();}catch(Exception ex){toast("Không lưu được sự kiện");}
        }).show();
    }
    void apkNotice(){new AlertDialog.Builder(this).setTitle("Xuất APK").setMessage("Chức năng đóng gói APK của game người dùng chưa được tích hợp ở bản nền tảng này. Hiện có thể lưu dự án ZIP ngoại tuyến. Bộ xuất APK trực tiếp là một cột mốc kỹ thuật tiếp theo, không phải tính năng đã hoàn thành.").setPositiveButton("Xuất ZIP", (d,w)->exportZip()).setNegativeButton("Đóng",null).show();}
    void exportZip(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,safe(project.optString("name","Game"))+".zip");try{startActivityForResult(i,EXPORT_ZIP);}catch(Exception e){toast("Không mở được trình chọn file");}}
    void importJson(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","application/zip","application/octet-stream"});try{startActivityForResult(i,IMPORT_PROJECT);}catch(Exception e){toast("Không mở được trình chọn file");}}
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        try{
            if(req==EXPORT_ZIP){
                try(OutputStream raw=getContentResolver().openOutputStream(uri);ZipOutputStream z=new ZipOutputStream(raw)){
                    save();z.putNextEntry(new ZipEntry("project.gsproj"));z.write(project.toString(2).getBytes(StandardCharsets.UTF_8));z.closeEntry();
                    z.putNextEntry(new ZipEntry("README.txt"));z.write("GameStudio Offline project format v1".getBytes(StandardCharsets.UTF_8));z.closeEntry();
                    File[] assets=assetDir().listFiles();if(assets!=null)for(File asset:assets){if(!asset.isFile())continue;z.putNextEntry(new ZipEntry("assets/"+asset.getName()));try(InputStream in=new FileInputStream(asset)){z.write(readAll(in));}z.closeEntry();}
                }toast("Đã xuất dự án ZIP kèm tài nguyên");
            }else if(req==IMPORT_IMAGE){assignImage(uri);}
            else if(req==IMPORT_PROJECT){try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Không đọc được tệp");importProjectBytes(readAll(in));}}
        }catch(Exception e){toast("Không xử lý được file: "+e.getMessage());}
    }
    class SceneView extends View{
        Paint p=new Paint(3);boolean run,three;float dx,dy;double sx,sy;boolean drag;
        SceneView(boolean r,boolean d){super(MainActivity.this);run=r;three=d;setBackgroundColor(bg);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.rgb(20,33,51));if(run)tickRuntime();float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return;c.save();c.translate((getWidth()-960*scale)/2,(getHeight()-540*scale)/2);c.scale(scale,scale);if(three)draw3(c);else draw2(c);c.restore();p.setColor(muted);p.setTextSize(18);c.drawText(run?(three?"CHẠY THỬ 3D":"CHẠY THỬ 2D"):(three?"3D VIEW":"2D SCENE"),12,24,p);if(run){p.setColor(Color.WHITE);p.setTextSize(20);c.drawText("Score: "+runtimeScore+"   Lives: "+runtimeLives,12,52,p);postInvalidateDelayed(40);}}
        void draw2(Canvas c){p.setColor(Color.rgb(100,180,242));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(129,201,252));for(int x=0;x<=960;x+=40)c.drawLine(x,0,x,540,p);for(int y=0;y<=540;y+=40)c.drawLine(0,y,960,y,p);JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||(run&&hiddenRuntimeObjects.contains(o.optString("name"))))continue;float x=(float)o.optDouble("x"),y=(float)o.optDouble("y"),w=(float)o.optDouble("w"),h=(float)o.optDouble("h");String type=o.optString("type");if(run&&type.equals("player")){x=(float)runtimePlayerX;y=(float)runtimePlayerY;}p.setColor(o.optInt("color",Color.WHITE));Bitmap sprite=loadSprite(o.optString("image",""));if(sprite!=null)c.drawBitmap(sprite,null,new RectF(x,y,x+Math.max(1,w),y+Math.max(1,h)),p);else if(type.equals("coin"))c.drawCircle(x+w/2,y+h/2,Math.max(w,h)/2,p);else if(type.equals("platform")){c.drawRect(x,y,x+Math.max(40,w),y+Math.max(20,h),p);p.setColor(Color.rgb(117,73,45));c.drawRect(x,y+h*.6f,x+Math.max(40,w),y+Math.max(20,h),p);}else if(type.equals("camera")||type.equals("light")){p.setTextSize(25);c.drawText(type.equals("camera")?"CAM":"LGT",x+8,y+32,p);}else{c.drawRoundRect(x,y,x+Math.max(34,w),y+Math.max(34,h),8,8,p);p.setColor(Color.WHITE);p.setTextSize(13);c.drawText(o.optString("name"),x,y-4,p);}if(!run&&i==selected){p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRect(x-4,y-4,x+Math.max(34,w)+4,y+Math.max(34,h)+4,p);p.setStyle(Paint.Style.FILL);}}}
        void draw3(Canvas c){p.setColor(Color.rgb(34,51,73));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(65,87,111));for(int i=-800;i<1600;i+=50){c.drawLine(480+i,0,480+i*.45f,540,p);c.drawLine(0,270+i*.24f,960,270+i*.24f,p);}JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||(run&&hiddenRuntimeObjects.contains(o.optString("name"))))continue;String type=o.optString("type");p.setColor(o.optInt("color",Color.BLUE));if(type.equals("camera")||type.equals("light")){p.setTextSize(22);c.drawText(type.equals("camera")?"◉ Camera":"☼ Light",35+i*20,55+i*24,p);continue;}float x=(float)o.optDouble("x"),y=(float)o.optDouble("y"),s=Math.max(30,(float)o.optDouble("w",60));x=480+(x-480)*.72f-(y-270)*.35f;y=280+(y-270)*.48f+(x-480)*.18f;Path path=new Path();path.moveTo(x,y-s);path.lineTo(x+s/2,y-s+s/3);path.lineTo(x,y-s+2*s/3);path.lineTo(x-s/2,y-s+s/3);path.close();p.setColor(o.optInt("color",Color.BLUE));c.drawPath(path,p);p.setColor(Color.rgb(35,74,138));c.drawRect(x-s/2,y-s+s/3,x,y+s/3,p);p.setColor(Color.rgb(27,57,111));c.drawRect(x,y-s+2*s/3,x+s/2,y+s/3,p);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getX();dy=e.getY();if(run){jumpRequested=true;invalidate();return true;}JSONArray a=objects();double best=Double.MAX_VALUE;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);double ox=(dx-getWidth()/2f+480*scale)/scale,oy=(dy-getHeight()/2f+270*scale)/scale;double dd=Math.pow(ox-o.optDouble("x")-o.optDouble("w")/2,2)+Math.pow(oy-o.optDouble("y")-o.optDouble("h")/2,2);if(dd<best){best=dd;selected=i;}}if(selected<a.length()){JSONObject o=a.optJSONObject(selected);sx=o.optDouble("x");sy=o.optDouble("y");drag=true;}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&drag){JSONObject o=objects().optJSONObject(selected);try{o.put("x",Math.max(0,Math.min(900,sx+(e.getX()-dx)/scale)));o.put("y",Math.max(0,Math.min(500,sy+(e.getY()-dy)/scale)));}catch(Exception ignored){}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(drag)save();drag=false;return true;}return true;}
    }
}
