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

    boolean finiteNumber(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}

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
    void validateImportedProject(JSONObject loaded)throws IOException{
        if(loaded==null)throw new IOException("Tệp dự án trống");
        String name=loaded.optString("name","").trim();
        if(name.isEmpty())throw new IOException("Dự án thiếu tên");
        String dimension=loaded.optString("dimension","2D").trim();
        if(!dimension.equalsIgnoreCase("2D")&&!dimension.equalsIgnoreCase("3D"))
            throw new IOException("Loại cảnh không hợp lệ (chỉ nhận 2D hoặc 3D)");
        int width=loaded.optInt("width",960),height=loaded.optInt("height",540);
        if(width<1||height<1||width>100000||height>100000)
            throw new IOException("Kích thước cảnh không hợp lệ");
        JSONArray objects=loaded.optJSONArray("objects");
        if(objects==null)throw new IOException("Dự án thiếu danh sách đối tượng");
        if(objects.length()>5000)throw new IOException("Dự án có quá nhiều đối tượng (tối đa 5000)");
        for(int i=0;i<objects.length();i++){
            JSONObject o=objects.optJSONObject(i);
            if(o==null)throw new IOException("Đối tượng thứ "+(i+1)+" không hợp lệ");
            if(o.optString("name","").trim().isEmpty())
                throw new IOException("Đối tượng thứ "+(i+1)+" thiếu tên");
            double x=o.optDouble("x",Double.NaN),y=o.optDouble("y",Double.NaN);
            double w=o.optDouble("w",Double.NaN),h=o.optDouble("h",Double.NaN);
            double health=o.optDouble("health",100),vx=o.optDouble("vx",0),vy=o.optDouble("vy",0);
            if(!finiteNumber(x)||!finiteNumber(y)||!finiteNumber(w)||!finiteNumber(h)||
               !finiteNumber(health)||!finiteNumber(vx)||!finiteNumber(vy)||w<0||h<0||health<0)
                throw new IOException("Dữ liệu số của đối tượng "+o.optString("name")+" không hợp lệ");
        }
        JSONArray variables=loaded.optJSONArray("variables");
        if(variables!=null){
            if(variables.length()>1000)throw new IOException("Dự án có quá nhiều biến (tối đa 1000)");
            for(int i=0;i<variables.length();i++){
                JSONObject v=variables.optJSONObject(i);
                if(v==null||!v.optString("name","").matches("[A-Za-z_][A-Za-z0-9_]*")||
                   !finiteNumber(v.optDouble("value",Double.NaN)))
                    throw new IOException("Biến thứ "+(i+1)+" không hợp lệ");
            }
        }
        JSONArray events=loaded.optJSONArray("events");
        if(events!=null){
            if(events.length()>10000)throw new IOException("Dự án có quá nhiều khối sự kiện (tối đa 10000)");
            for(int i=0;i<events.length();i++){
                JSONObject e=events.optJSONObject(i);
                if(e==null||e.optString("trigger","").trim().isEmpty())
                    throw new IOException("Khối sự kiện thứ "+(i+1)+" không hợp lệ");
            }
        }
    }

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
            validateImportedProject(loaded);
            project=loaded;File ad=assetDir();for(Map.Entry<String,byte[]> entry:importedAssets.entrySet()){try(FileOutputStream out=new FileOutputStream(new File(ad,entry.getKey()))){out.write(entry.getValue());}}
        }else{
            JSONObject loaded=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            validateImportedProject(loaded);
            project=loaded;
        }
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
    JSONObject variable(String name,double value)throws Exception{JSONObject v=new JSONObject();v.put("name",name);v.put("value",value);return v;}

    void home(){
        preview=false;LinearLayout root=col();
        root.addView(label("🎮 GameStudio Offline",25,fg,true));
        root.addView(label("Tạo game 2D và 3D • No-code • Ưu tiên offline",13,muted,false));
        root.addView(label("Dự án mới",18,fg,true));
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setHint("Tên dự án");nameBox.setHintTextColor(muted);nameBox.setTextColor(fg);nameBox.setBackgroundColor(panel);root.addView(nameBox,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout creation=row();creation.addView(btn("Tạo game 2D",()->create("2D")));creation.addView(btn("Tạo game 3D",()->create("3D")));root.addView(creation);
        root.addView(btn("Tạo mẫu game (Đi cảnh / Bắn súng / RPG / Tower Defense)",this::showTemplatePicker));
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
            project.put("objects",os);
            JSONArray vars=new JSONArray();vars.put(variable("Score",0));vars.put(variable("Lives",3));vars.put(variable("Timer",0));project.put("variables",vars);
            JSONArray ev=new JSONArray();ev.put(event("Khi bắt đầu cảnh","Đặt biến Score = 0"));
            ev.put(event(dimension.equals("2D")?"Khi Player chạm Coin":"Khi bắt đầu cảnh",dimension.equals("2D")?"Tăng Score thêm 1 và ẩn Coin":"Đặt Camera nhìn vào Cube"));project.put("events",ev);
            activeFile=new File(dir(),safe(n)+".gsproj");tab=dimension;save();selected=0;editor();
        }catch(Exception e){toast("Lỗi tạo dự án: "+e.getMessage());}
    }

    JSONObject sceneObject(String n,String type,double x,double y,double w,double h,int color,double health,double vx,double vy)throws Exception{
        JSONObject o=obj(n,type,x,y,w,h,color);o.put("health",health);o.put("vx",vx);o.put("vy",vy);return o;
    }
    JSONObject templateEvent(String trigger,String action,String customAction,String condition)throws Exception{
        JSONObject e=event(trigger,action);e.put("customAction",customAction==null?"":customAction);e.put("condition",condition==null?"":condition);return e;
    }
    void showTemplatePicker(){
        new AlertDialog.Builder(this).setTitle("Chọn mẫu game 2D").setItems(new String[]{"Đi cảnh cơ bản","Bắn súng 2D","RPG sinh tồn cơ bản","Tower Defense cơ bản"},(d,which)->{
            if(which==0)create("2D");else if(which==1)createTemplate("shooter");else if(which==2)createTemplate("rpg");else createTemplate("tower");
        }).setNegativeButton("Hủy",null).show();
    }
    void createTemplate(String kind){
        String n=nameBox.getText().toString().trim();boolean shooter="shooter".equals(kind),tower="tower".equals(kind);
        if(n.isEmpty())n=shooter?"ShooterStarter":tower?"TowerDefenseStarter":"RPGStarter";
        try{
            project=new JSONObject();project.put("schema",1);project.put("name",n);project.put("dimension","2D");project.put("width",960);project.put("height",540);
            JSONArray os=new JSONArray();
            if(!tower)os.put(sceneObject("Player","player",75,350,48,60,Color.rgb(40,132,255),100,0,0));
            os.put(sceneObject("Ground","platform",0,465,960,75,Color.rgb(56,174,99),100,0,0));
            if(tower){os.put(sceneObject("Enemy","enemy",-120,350,44,44,Color.RED,100,0,0));os.put(sceneObject("Core","cube",830,350,70,70,Color.rgb(40,132,255),100,0,0));}
            else{os.put(sceneObject("Enemy","enemy",shooter?610:570,380,48,55,Color.RED,100,0,0));os.put(sceneObject("Enemy2","enemy",shooter?790:750,350,48,55,Color.rgb(220,75,80),100,0,0));os.put(sceneObject("Coin","coin",320,405,30,30,Color.YELLOW,1,0,0));os.put(sceneObject("Projectile","projectile",-120,-120,18,12,Color.YELLOW,1,0,0));}
            project.put("objects",os);
            JSONArray vars=new JSONArray();vars.put(variable("Score",0));vars.put(variable("Lives",tower?10:3));vars.put(variable("Timer",0));
            if(!shooter&&!tower){vars.put(variable("Gold",0));vars.put(variable("Level",1));}
            project.put("variables",vars);
            JSONArray ev=new JSONArray();
            ev.put(event("Khi bắt đầu cảnh","Đặt biến Score = 0"));
            if(shooter){
                ev.put(event("Khi bấm nút Bắn","Bắn Projectile"));
                ev.put(event("Khi Projectile chạm Enemy","Trừ 25 máu Enemy vừa trúng"));
                ev.put(event("Khi Projectile chạm Enemy","Hủy Projectile vừa trúng"));
                ev.put(templateEvent("Khi điều kiện chuyển thành đúng","Loại bỏ Enemy vừa trúng","", "LastHitEnemy.Health <= 0"));
                ev.put(templateEvent("Khi điều kiện chuyển thành đúng","Tăng Score thêm 10","", "LastHitEnemy.Health <= 0"));
                ev.put(event("Khi Player chạm Enemy","Trừ 10 máu Player"));
                ev.put(event("Khi Player chạm Coin","Tăng Score thêm 1"));
                ev.put(event("Khi Player chạm Coin","Ẩn Coin"));
            }else if(tower){
                ev.put(templateEvent("Mỗi 3 giây","Lệnh tùy chỉnh (xem ô bên dưới)","spawn Enemy 0 350 90 0",""));
                ev.put(event("Mỗi 1 giây","Tăng Score thêm 1"));
                ev.put(event("Khi Enemy chạm Cube","Loại bỏ Enemy vừa trúng"));
                ev.put(event("Khi Enemy chạm Cube","Trừ 1 mạng"));
            }else{
                ev.put(templateEvent("Khi bắt đầu cảnh","Lệnh tùy chỉnh (xem ô bên dưới)","follow Enemy Player 35",""));
                ev.put(templateEvent("Khi bắt đầu cảnh","Lệnh tùy chỉnh (xem ô bên dưới)","follow Enemy2 Player 45",""));
                ev.put(event("Khi Player chạm Coin","Tăng Score thêm 1"));
                ev.put(templateEvent("Khi Player chạm Coin","Lệnh tùy chỉnh (xem ô bên dưới)","add Gold 5",""));
                ev.put(event("Khi Player chạm Coin","Ẩn Coin"));
                ev.put(event("Khi Player chạm Enemy","Trừ 10 máu Player"));
                ev.put(templateEvent("Mỗi 5 giây","Lệnh tùy chỉnh (xem ô bên dưới)","set Player.Health = Player.Health + 5",""));
                ev.put(templateEvent("Khi điều kiện chuyển thành đúng","Lệnh tùy chỉnh (xem ô bên dưới)","add Level 1","Score >= Level * 10"));
            }
            project.put("events",ev);
            activeFile=new File(dir(),safe(n)+".gsproj");tab="2D";preview=false;selected=0;save();editor();
            toast(shooter?"Đã tạo mẫu Bắn súng 2D":tower?"Đã tạo mẫu Tower Defense":"Đã tạo mẫu RPG sinh tồn");
        }catch(Exception e){toast("Không tạo được mẫu game: "+e.getMessage());}
    }
    int selected=0;
    final String[] triggerChoices={"Khi bắt đầu cảnh","Mỗi khung hình","Mỗi 1 giây","Mỗi 5 giây","Mỗi 10 giây","Khi Player chạm Coin","Khi Player chạm Enemy","Khi Player chạm Cube","Khi Player chạm Platform","Khi Player chạm bất kỳ đối tượng nào","Khi Projectile chạm Enemy","Khi Enemy chạm Coin","Khi hai đối tượng chạm nhau","Khi bấm nút Nhảy","Khi bấm nút Bắn","Khi điều kiện chuyển thành đúng","Trong khi điều kiện đúng","Khi Player rơi khỏi màn hình"};
    final String[] actionChoices={"Đặt biến Score = 0","Tăng Score thêm 1","Tăng Score thêm 10","Giảm Score đi 1","Đặt biến Lives = 3","Tăng Lives thêm 1","Ẩn Coin","Hiện Coin","Ẩn Enemy","Hiện Enemy","Cho Player nhảy","Trừ 1 mạng","Di chuyển Player","Di chuyển Player sang trái","Di chuyển Player sang phải","Hồi sinh Player","Đặt Gravity = 950","Đặt MoveSpeed = 240","Đặt JumpStrength = 430","Cho Enemy tự đi sang phải","Cho Enemy tự đi sang trái","Dừng Enemy","Cho Enemy đuổi theo Player","Dừng Enemy đuổi theo Player","Bắn Projectile","Trừ 10 máu Player","Trừ 25 máu Enemy","Trừ 25 máu Enemy vừa trúng","Hủy Projectile vừa trúng","Loại bỏ Enemy vừa trúng","Hồi 25 máu Player","Đưa Player về điểm xuất phát","Lệnh tùy chỉnh (xem ô bên dưới)"};
    // Runtime state is separate from editor data; play-testing never changes saved object positions.
    final Map<String,Double> runtimeVars = new HashMap<>();
    final Map<String,Double> runtimeObjectX = new HashMap<>();
    final Map<String,Double> runtimeObjectY = new HashMap<>();
    final Map<String,Double> runtimeObjectVX = new HashMap<>();
    final Map<String,Double> runtimeObjectVY = new HashMap<>();
    final Map<String,Double> runtimeObjectHealth = new HashMap<>();
    final Map<String,Double> runtimeFollowSpeed = new HashMap<>();
    String lastObjectCollisionA="",lastObjectCollisionB="",lastObjectCollisionTypeA="",lastObjectCollisionTypeB="";
    final ArrayList<JSONObject> runtimeSpawnedObjects = new ArrayList<>();
    int runtimeSpawnCounter=0;
    final Set<String> hiddenRuntimeObjects = new HashSet<>();
    final Set<String> destroyedRuntimeObjects = new HashSet<>();
    final Set<String> previousContacts = new HashSet<>();
    final Map<Integer,Long> eventTimerLastFired = new HashMap<>();
    final Map<Integer,Boolean> eventConditionWasTrue = new HashMap<>();
    double runtimePlayerX=120, runtimePlayerY=350, runtimePlayerVX=0, runtimePlayerVY=0;
    double runtimeSpawnX=120, runtimeSpawnY=350;
    double runtimeGravity=950, runtimeMoveSpeed=240, runtimeJumpStrength=430, runtimeTimeScale=1;
    float moveAxis=0;
    boolean runtimeGrounded=false, jumpRequested=false, runtimeStarted=false, runtimeGameOver=false;
    int runtimeScore=0, runtimeLives=3;
    long lastRuntimeTick=0, runtimeStartMillis=0;

    void startRuntime(){
        runtimeVars.clear();runtimeVars.put("Score",0d);runtimeVars.put("Lives",3d);runtimeVars.put("Timer",0d);
        JSONArray savedVars=project.optJSONArray("variables");
        if(savedVars!=null)for(int i=0;i<savedVars.length();i++){JSONObject v=savedVars.optJSONObject(i);if(v!=null){String n=v.optString("name","").trim();if(!n.isEmpty())putRuntimeVar(n,v.optDouble("value",0));}}
        runtimeSpawnedObjects.clear();runtimeSpawnCounter=0;runtimeFollowSpeed.clear();lastObjectCollisionA="";lastObjectCollisionB="";lastObjectCollisionTypeA="";lastObjectCollisionTypeB="";runtimeObjectX.clear();runtimeObjectY.clear();runtimeObjectVX.clear();runtimeObjectVY.clear();runtimeObjectHealth.clear();JSONArray sceneObjects=objects();for(int i=0;i<sceneObjects.length();i++){JSONObject o=sceneObjects.optJSONObject(i);if(o!=null){String n=o.optString("name","");runtimeObjectX.put(n,o.optDouble("x",0));runtimeObjectY.put(n,o.optDouble("y",0));runtimeObjectVX.put(n,o.optDouble("vx",0));runtimeObjectVY.put(n,o.optDouble("vy",0));runtimeObjectHealth.put(n,o.optDouble("health",100));}}
        hiddenRuntimeObjects.clear();destroyedRuntimeObjects.clear();previousContacts.clear();eventTimerLastFired.clear();eventConditionWasTrue.clear();
        runtimeGameOver=false;runtimeScore=0;runtimeLives=3;runtimeGravity=950;runtimeMoveSpeed=240;runtimeJumpStrength=430;runtimeTimeScale=1;
        runtimePlayerVX=0;runtimePlayerVY=0;moveAxis=0;jumpRequested=false;runtimeGrounded=false;
        JSONObject player=findPlayer();if(player!=null){runtimePlayerX=player.optDouble("x",120);runtimePlayerY=player.optDouble("y",350);runtimePlayerVX=player.optDouble("vx",0);}
        runtimeSpawnX=runtimePlayerX;runtimeSpawnY=runtimePlayerY;
        lastRuntimeTick=System.currentTimeMillis();runtimeStartMillis=lastRuntimeTick;runtimeStarted=true;
        executePhase("start",null);
    }
    void stopRuntime(){runtimeStarted=false;moveAxis=0;jumpRequested=false;hiddenRuntimeObjects.clear();destroyedRuntimeObjects.clear();previousContacts.clear();runtimeSpawnedObjects.clear();runtimeSpawnCounter=0;runtimeFollowSpeed.clear();runtimeObjectX.clear();runtimeObjectY.clear();runtimeObjectVX.clear();runtimeObjectVY.clear();runtimeObjectHealth.clear();}
    JSONArray runtimeSceneObjects(){JSONArray all=new JSONArray();JSONArray base=objects();for(int i=0;i<base.length();i++){JSONObject o=base.optJSONObject(i);if(o!=null)all.put(o);}for(JSONObject o:runtimeSpawnedObjects)if(o!=null)all.put(o);return all;}
    boolean runtimeTargetMatches(JSONObject o,String query,JSONArray all){
        String q=query.trim().toLowerCase(Locale.ROOT),name=o.optString("name","").toLowerCase(Locale.ROOT),type=o.optString("type","").toLowerCase(Locale.ROOT);boolean exactNameExists=false;
        for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&item.optString("name","").equalsIgnoreCase(query.trim())){exactNameExists=true;break;}}
        return exactNameExists?name.equals(q):(type.equals(q)||name.contains(q));
    }
    void cleanupRuntimeSpawns(){
        java.util.Iterator<JSONObject> it=runtimeSpawnedObjects.iterator();while(it.hasNext()){JSONObject o=it.next();String n=o.optString("name","");double x=runtimeObjectCoordinate(o,true),y=runtimeObjectCoordinate(o,false);if(destroyedRuntimeObjects.contains(n)||x < -160||x > 1120||y < -160||y > 700){it.remove();runtimeObjectX.remove(n);runtimeObjectY.remove(n);runtimeObjectVX.remove(n);runtimeObjectVY.remove(n);runtimeObjectHealth.remove(n);runtimeFollowSpeed.remove(n);hiddenRuntimeObjects.remove(n);destroyedRuntimeObjects.remove(n);previousContacts.remove(o.optString("type","")+":"+n);previousContacts.removeIf(k->k.contains(n));}}
    }
    JSONObject findObject(String query){
        if(query==null)return null;String q=query.trim();JSONArray a=objects();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("name","").equalsIgnoreCase(q))return o;}
        for(JSONObject o:runtimeSpawnedObjects)if(o!=null&&o.optString("name","").equalsIgnoreCase(q))return o;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("type","").equalsIgnoreCase(q))return o;}
        for(JSONObject o:runtimeSpawnedObjects)if(o!=null&&o.optString("type","").equalsIgnoreCase(q))return o;
        return null;
    }
    void spawnRuntimeObject(String templateQuery,double x,double y,double vx,double vy){
        if(runtimeSpawnedObjects.size()>=200)return;
        JSONObject template=null;JSONArray base=objects();String q=templateQuery.trim();
        for(int i=0;i<base.length();i++){JSONObject o=base.optJSONObject(i);if(o!=null&&o.optString("name","").equalsIgnoreCase(q)){template=o;break;}}
        if(template==null)for(int i=0;i<base.length();i++){JSONObject o=base.optJSONObject(i);if(o!=null&&o.optString("type","").equalsIgnoreCase(q)){template=o;break;}}
        if(template==null||isPlayerObject(template)){toast("Không tìm thấy mẫu phù hợp để tạo: "+q);return;}
        try{JSONObject instance=new JSONObject(template.toString());String baseName=template.optString("name",q).replaceAll("\\s+","_");String instanceName="Spawned_"+baseName+"_"+(++runtimeSpawnCounter);instance.put("name",instanceName);instance.put("x",x);instance.put("y",y);instance.put("vx",Double.isNaN(vx)?template.optDouble("vx",0):vx);instance.put("vy",Double.isNaN(vy)?template.optDouble("vy",0):vy);runtimeSpawnedObjects.add(instance);runtimeObjectX.put(instanceName,x);runtimeObjectY.put(instanceName,y);runtimeObjectVX.put(instanceName,instance.optDouble("vx",0));runtimeObjectVY.put(instanceName,instance.optDouble("vy",0));runtimeObjectHealth.put(instanceName,instance.optDouble("health",100));}
        catch(Exception e){toast("Không tạo được đối tượng runtime");}
    }
    String resolveLastHitTarget(String target){if(target==null)return "";if(target.equalsIgnoreCase("HitA"))return lastObjectCollisionA;if(target.equalsIgnoreCase("HitB"))return lastObjectCollisionB;if(target.equalsIgnoreCase("LastHitEnemy")){if(lastObjectCollisionTypeA.equalsIgnoreCase("enemy"))return lastObjectCollisionA;if(lastObjectCollisionTypeB.equalsIgnoreCase("enemy"))return lastObjectCollisionB;return target;}if(target.equalsIgnoreCase("LastHitProjectile")){if(lastObjectCollisionTypeA.equalsIgnoreCase("projectile"))return lastObjectCollisionA;if(lastObjectCollisionTypeB.equalsIgnoreCase("projectile"))return lastObjectCollisionB;return target;}return target;}
    boolean isPlayerObject(JSONObject o){return o!=null&&(o.optString("type","").equalsIgnoreCase("player")||o.optString("name","").equalsIgnoreCase("player"));}
    double runtimeObjectCoordinate(JSONObject o,boolean xAxis){
        if(o==null)return 0;if(isPlayerObject(o))return xAxis?runtimePlayerX:runtimePlayerY;
        String n=o.optString("name","");Map<String,Double> values=xAxis?runtimeObjectX:runtimeObjectY;
        return values.getOrDefault(n,o.optDouble(xAxis?"x":"y",0));
    }
    void setObjectCoordinate(String query,boolean xAxis,double value){
        JSONObject o=findObject(query);if(o==null)return;
        if(isPlayerObject(o)){if(xAxis)runtimePlayerX=value;else runtimePlayerY=value;return;}
        (xAxis?runtimeObjectX:runtimeObjectY).put(o.optString("name",""),value);
    }
    void teleportObject(String query,double x,double y){JSONObject o=findObject(query);if(o==null)return;setObjectCoordinate(query,true,x);setObjectCoordinate(query,false,y);if(isPlayerObject(o))runtimePlayerVY=0;}
    double runtimeObjectVelocity(JSONObject o,boolean xAxis){if(o==null)return 0;if(isPlayerObject(o))return xAxis?runtimePlayerVX:runtimePlayerVY;String n=o.optString("name","");return (xAxis?runtimeObjectVX:runtimeObjectVY).getOrDefault(n,o.optDouble(xAxis?"vx":"vy",0));}
    void setObjectVelocity(String query,boolean xAxis,double value){JSONObject o=findObject(query);if(o==null)return;if(isPlayerObject(o)){if(xAxis)runtimePlayerVX=value;else runtimePlayerVY=value;return;}(xAxis?runtimeObjectVX:runtimeObjectVY).put(o.optString("name",""),value);}
    double runtimeObjectHealthValue(JSONObject o){if(o==null)return 0;return runtimeObjectHealth.getOrDefault(o.optString("name",""),o.optDouble("health",100));}
    void setObjectHealth(String query,double value){JSONObject o=findObject(query);if(o!=null){double max=Math.max(0,o.optDouble("health",100));runtimeObjectHealth.put(o.optString("name",""),Math.max(0,Math.min(max,value)));}}
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
            else if("shoot".equals(phase))match=trigger.contains("bấm nút bắn")||trigger.contains("nút bắn");
            else if("objectCollision".equals(phase)){match=trigger.contains("chạm")&&contactType!=null;if(match&&!trigger.contains("hai đối tượng")){String[] types=contactType.split("\\|");match=types.length==2&&trigger.contains(types[0])&&trigger.contains(types[1]);}}
            else if("collision".equals(phase))match=trigger.contains("chạm")&&contactType!=null&&(trigger.contains(contactType.toLowerCase(Locale.ROOT))||trigger.contains("bất kỳ đối tượng nào"));
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
        if(token==null)return null;
        String s=token.trim();
        Double simple=resolveSimpleValue(s);if(simple!=null)return finiteNumber(simple)?simple:null;
        try{ExpressionParser parser=new ExpressionParser(s);double value=parser.expression();parser.spaces();if(parser.pos==s.length()&&finiteNumber(value))return value;}catch(Exception ignored){}
        return null;
    }
    Double resolveSimpleValue(String token){
        String s=token.trim();
        try{return Double.parseDouble(s);}catch(Exception ignored){}
        if("Player.X".equalsIgnoreCase(s))return runtimePlayerX;
        if("Player.Y".equalsIgnoreCase(s))return runtimePlayerY;
        if("Player.VX".equalsIgnoreCase(s))return runtimePlayerVX;
        Matcher objectCoordinate=Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\.(X|Y|VX|VY|Health)$",Pattern.CASE_INSENSITIVE).matcher(s);
        if(objectCoordinate.matches()){JSONObject o=findObject(resolveLastHitTarget(objectCoordinate.group(1)));if(o!=null){String property=objectCoordinate.group(2);if(property.equalsIgnoreCase("Health"))return runtimeObjectHealthValue(o);if(property.equalsIgnoreCase("VX"))return runtimeObjectVelocity(o,true);if(property.equalsIgnoreCase("VY"))return runtimeObjectVelocity(o,false);return runtimeObjectCoordinate(o,property.equalsIgnoreCase("X"));}}
        if("Player.VY".equalsIgnoreCase(s))return runtimePlayerVY;
        if("Gravity".equalsIgnoreCase(s))return runtimeGravity;
        if("MoveSpeed".equalsIgnoreCase(s))return runtimeMoveSpeed;
        if("JumpStrength".equalsIgnoreCase(s))return runtimeJumpStrength;
        if("TimeScale".equalsIgnoreCase(s))return runtimeTimeScale;
        if("Timer".equalsIgnoreCase(s))return Math.max(0,(System.currentTimeMillis()-runtimeStartMillis)/1000d);
        for(Map.Entry<String,Double> entry:runtimeVars.entrySet())if(entry.getKey().equalsIgnoreCase(s))return entry.getValue();
        return null;
    }
    class ExpressionParser{
        final String s;int pos=0;
        ExpressionParser(String input){s=input;}
        void spaces(){while(pos<s.length()&&Character.isWhitespace(s.charAt(pos)))pos++;}
        boolean take(char c){spaces();if(pos<s.length()&&s.charAt(pos)==c){pos++;return true;}return false;}
        double expression(){double v=term();while(true){if(take('+'))v+=term();else if(take('-'))v-=term();else return v;}}
        double term(){double v=factor();while(true){if(take('*'))v*=factor();else if(take('/')){double d=factor();if(Math.abs(d)<1e-12)throw new ArithmeticException("division by zero");v/=d;}else if(take('%')){double d=factor();if(Math.abs(d)<1e-12)throw new ArithmeticException("division by zero");v%=d;}else return v;}}
        double factor(){
            spaces();if(take('+'))return factor();if(take('-'))return -factor();
            if(take('(')){double v=expression();if(!take(')'))throw new IllegalArgumentException("missing )");return v;}
            spaces();int start=pos;
            if(pos<s.length()&&(Character.isDigit(s.charAt(pos))||s.charAt(pos)=='.')){
                boolean dot=false;while(pos<s.length()){char ch=s.charAt(pos);if(Character.isDigit(ch)){pos++;continue;}if(ch=='.'&&!dot){dot=true;pos++;continue;}break;}
                return Double.parseDouble(s.substring(start,pos));
            }
            while(pos<s.length()){char ch=s.charAt(pos);if(Character.isLetterOrDigit(ch)||ch=='_'||ch=='.')pos++;else break;}
            if(start==pos)throw new IllegalArgumentException("expected value");
            Double value=resolveSimpleValue(s.substring(start,pos));if(value==null)throw new IllegalArgumentException("unknown variable");return value;
        }
    }
    void putRuntimeVar(String name,double value){
        String key=name.trim();for(String existing:runtimeVars.keySet())if(existing.equalsIgnoreCase(key)){key=existing;break;}runtimeVars.put(key,value);
    }
    void updateHudValues(){
        runtimeScore=(int)Math.round(runtimeVars.getOrDefault("Score",0d));
        runtimeLives=(int)Math.round(runtimeVars.getOrDefault("Lives",3d));
    }
    void hideMatchingObjects(String query,boolean hidden){
        String q=query.trim().toLowerCase(Locale.ROOT);JSONArray a=runtimeSceneObjects();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String name=o.optString("name","").toLowerCase(Locale.ROOT),type=o.optString("type","").toLowerCase(Locale.ROOT);
            if(runtimeTargetMatches(o,query,a)){if(hidden)hiddenRuntimeObjects.add(o.optString("name",""));else hiddenRuntimeObjects.remove(o.optString("name",""));}
        }
    }
    void destroyMatchingObjects(String query){
        String q=query.trim().toLowerCase(Locale.ROOT);JSONArray a=runtimeSceneObjects();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String name=o.optString("name","").toLowerCase(Locale.ROOT),type=o.optString("type","").toLowerCase(Locale.ROOT);
            if(runtimeTargetMatches(o,query,a))destroyedRuntimeObjects.add(o.optString("name",""));
        }
    }
    void setRuntimeValue(String key,double value){
        if(!finiteNumber(value))return;
        Matcher objectCoordinate=Pattern.compile("(?i)^([A-Za-z_][A-Za-z0-9_]*)\.(X|Y|VX|VY|Health)$").matcher(key);
        if(objectCoordinate.matches()){
            String target=resolveLastHitTarget(objectCoordinate.group(1));
            JSONObject object=findObject(target);
            if(object!=null){
                String property=objectCoordinate.group(2);
                if(property.equalsIgnoreCase("Health"))setObjectHealth(target,value);
                else if(property.equalsIgnoreCase("VX"))setObjectVelocity(target,true,value);
                else if(property.equalsIgnoreCase("VY"))setObjectVelocity(target,false,value);
                else setObjectCoordinate(target,property.equalsIgnoreCase("X"),value);
                return;
            }
        }
        if(key.equalsIgnoreCase("Player.X"))runtimePlayerX=value;
        else if(key.equalsIgnoreCase("Player.Y"))runtimePlayerY=value;
        else if(key.equalsIgnoreCase("Player.VX"))runtimePlayerVX=value;
        else if(key.equalsIgnoreCase("Player.VY"))runtimePlayerVY=value;
        else if(key.equalsIgnoreCase("Gravity"))runtimeGravity=Math.max(0,value);
        else if(key.equalsIgnoreCase("MoveSpeed"))runtimeMoveSpeed=Math.max(0,value);
        else if(key.equalsIgnoreCase("JumpStrength"))runtimeJumpStrength=Math.max(0,value);
        else if(key.equalsIgnoreCase("TimeScale"))runtimeTimeScale=Math.max(0,Math.min(3,value));
        else putRuntimeVar(key,value);
    }

    void runAction(String action){
        String low=action.toLowerCase(Locale.ROOT);
        String command=action.trim();
        Matcher customSet=Pattern.compile("(?i)^set\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(.+)$").matcher(command);
        Matcher customAdd=Pattern.compile("(?i)^add\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?)\\s+(.+)$").matcher(command);
        Matcher customSub=Pattern.compile("(?i)^subtract\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?)\\s+(.+)$").matcher(command);
        Matcher teleport=Pattern.compile("(?i)^teleport\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher spawnAtPlayer=Pattern.compile("(?i)^spawn\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+at\\s+player(?:\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?))?$").matcher(command);
        Matcher spawn=Pattern.compile("(?i)^spawn\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)(?:\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?))?$").matcher(command);
        Matcher follow=Pattern.compile("(?i)^follow\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+player\\s+(\\d+(?:\\.\\d+)?)$").matcher(command);
        Matcher stopFollow=Pattern.compile("(?i)^stopfollow\\s+([A-Za-z_][A-Za-z0-9_]*)$").matcher(command);
        Matcher hide=Pattern.compile("(?i)^(hide|show|destroy)\\s+(.+)$").matcher(command);
        if(customSet.matches()){
            String key=customSet.group(1);Double calculated=resolveValue(customSet.group(2));if(calculated==null)return;
            setRuntimeValue(key,calculated);updateHudValues();return;
        }
        if(customAdd.matches()||customSub.matches()){
            Matcher m=customAdd.matches()?customAdd:customSub;
            String key=m.group(1);
            Double operand=resolveValue(m.group(2));
            if(operand==null)return;
            Double current=resolveValue(key);
            double base=current==null?0:current;
            double result=base+(m==customAdd?operand:-operand);
            if(!finiteNumber(result))return;
            setRuntimeValue(key,result);
            updateHudValues();
            return;
        }
        if(teleport.matches()){teleportObject(teleport.group(1),Double.parseDouble(teleport.group(2)),Double.parseDouble(teleport.group(3)));updateHudValues();return;}
        if(spawnAtPlayer.matches()){double vx=spawnAtPlayer.group(2)==null?Double.NaN:Double.parseDouble(spawnAtPlayer.group(2));double vy=spawnAtPlayer.group(3)==null?Double.NaN:Double.parseDouble(spawnAtPlayer.group(3));JSONObject p=findPlayer(),t=findObject(spawnAtPlayer.group(1));double spawnX=runtimePlayerX,spawnY=runtimePlayerY;if(p!=null&&t!=null){double pw=Math.max(1,p.optDouble("w",48)),ph=Math.max(1,p.optDouble("h",60)),tw=Math.max(1,t.optDouble("w",20)),th=Math.max(1,t.optDouble("h",20));spawnX=runtimePlayerX+(vx<0?-tw:pw);spawnY=runtimePlayerY+(ph-th)/2;}spawnRuntimeObject(spawnAtPlayer.group(1),spawnX,spawnY,vx,vy);updateHudValues();return;}
        if(spawn.matches()){double vx=spawn.group(4)==null?Double.NaN:Double.parseDouble(spawn.group(4));double vy=spawn.group(5)==null?Double.NaN:Double.parseDouble(spawn.group(5));spawnRuntimeObject(spawn.group(1),Double.parseDouble(spawn.group(2)),Double.parseDouble(spawn.group(3)),vx,vy);updateHudValues();return;}
        if(follow.matches()){JSONObject target=findObject(follow.group(1));if(target!=null&&!isPlayerObject(target))runtimeFollowSpeed.put(target.optString("name",""),Double.parseDouble(follow.group(2)));updateHudValues();return;}
        if(stopFollow.matches()){JSONObject target=findObject(stopFollow.group(1));if(target!=null){String n=target.optString("name","");runtimeFollowSpeed.remove(n);setObjectVelocity(n,true,0);setObjectVelocity(n,false,0);}updateHudValues();return;}
        if(hide.matches()){String verb=hide.group(1).toLowerCase(Locale.ROOT),target=hide.group(2);String actualTarget=resolveLastHitTarget(target);if(verb.equals("destroy"))destroyMatchingObjects(actualTarget);else hideMatchingObjects(actualTarget,verb.equals("hide"));updateHudValues();return;}
        if(command.equalsIgnoreCase("respawn Player")||command.equalsIgnoreCase("respawn player")){runtimePlayerX=runtimeSpawnX;runtimePlayerY=runtimeSpawnY;runtimePlayerVY=0;runtimeGrounded=false;updateHudValues();return;}
        Matcher set=Pattern.compile("(?i).*đặt(?:\\s+biến)?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Za-z0-9_ .+*/()%\\-]+).*").matcher(action);
        if(set.matches()){
            Double value=resolveValue(set.group(2));
            if(value!=null){setRuntimeValue(set.group(1),value);updateHudValues();return;}
        }
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
        if(low.contains("nhảy")||low.contains("jump")){if(runtimeGrounded){runtimePlayerVY=-runtimeJumpStrength;runtimeGrounded=false;}}
        if(low.contains("di chuyển player")||low.contains("move player")){if(low.contains("sang trái")||low.contains("left"))runtimePlayerX-=3;else if(low.contains("sang phải")||low.contains("right"))runtimePlayerX+=3;else runtimePlayerX+=2;runtimePlayerX=Math.max(0,Math.min(900,runtimePlayerX));}
        if(low.equals("bắn projectile")){JSONObject p=findPlayer(),t=findObject("Projectile");double x=runtimePlayerX,y=runtimePlayerY;if(p!=null&&t!=null){double pw=Math.max(1,p.optDouble("w",48)),ph=Math.max(1,p.optDouble("h",60)),tw=Math.max(1,t.optDouble("w",20)),th=Math.max(1,t.optDouble("h",20));x=runtimePlayerX+pw;y=runtimePlayerY+(ph-th)/2;}spawnRuntimeObject("Projectile",x,y,260,0);updateHudValues();return;}
        if(low.contains("dừng enemy đuổi theo player")){JSONObject enemy=findObject("Enemy");if(enemy!=null){String n=enemy.optString("name","");runtimeFollowSpeed.remove(n);setObjectVelocity(n,true,0);setObjectVelocity(n,false,0);}}
        if(low.contains("cho enemy đuổi theo player")){JSONObject enemy=findObject("Enemy");if(enemy!=null)runtimeFollowSpeed.put(enemy.optString("name",""),100d);}
        if(low.contains("cho enemy tự đi sang phải"))setObjectVelocity("Enemy",true,120);
        if(low.contains("cho enemy tự đi sang trái"))setObjectVelocity("Enemy",true,-120);
        if(low.contains("dừng enemy")){setObjectVelocity("Enemy",true,0);setObjectVelocity("Enemy",false,0);}
        if(low.contains("trừ 10 máu player")){JSONObject player=findPlayer();if(player!=null)setObjectHealth(player.optString("name","Player"),Math.max(0,runtimeObjectHealthValue(player)-10));}
        if(low.contains("trừ 25 máu enemy vừa trúng")){JSONObject enemy=findObject(resolveLastHitTarget("LastHitEnemy"));if(enemy!=null)setObjectHealth(enemy.optString("name","Enemy"),Math.max(0,runtimeObjectHealthValue(enemy)-25));}
        else if(low.contains("trừ 25 máu enemy")){JSONObject enemy=findObject("Enemy");if(enemy!=null)setObjectHealth(enemy.optString("name","Enemy"),Math.max(0,runtimeObjectHealthValue(enemy)-25));}
        if(low.contains("hủy projectile vừa trúng"))destroyMatchingObjects(resolveLastHitTarget("LastHitProjectile"));
        if(low.contains("loại bỏ enemy vừa trúng"))destroyMatchingObjects(resolveLastHitTarget("LastHitEnemy"));
        if(low.contains("hồi 25 máu player")){JSONObject p=findPlayer();if(p!=null)setObjectHealth(p.optString("name","Player"),runtimeObjectHealthValue(p)+25);}
        if(low.contains("đưa player về điểm xuất phát")){runtimePlayerX=runtimeSpawnX;runtimePlayerY=runtimeSpawnY;runtimePlayerVY=0;runtimeGrounded=false;}
        updateHudValues();
    }
    void setCoinsHidden(boolean hidden){
        JSONArray a=runtimeSceneObjects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("type").equalsIgnoreCase("coin")){if(hidden)hiddenRuntimeObjects.add(o.optString("name"));else hiddenRuntimeObjects.remove(o.optString("name"));}}
    }
    void tickRuntime(){
        if(!runtimeStarted)startRuntime();
        if(runtimeGameOver)return;
        long now=System.currentTimeMillis();double dt=Math.min(.05,Math.max(.001,(now-lastRuntimeTick)/1000d));lastRuntimeTick=now;
        JSONObject player=findPlayer();
        executePhase("frame",null);
        double pw=0,ph=0,oldBottom=runtimePlayerY;
        if(player!=null){
            if(jumpRequested){executePhase("jump",null);if(runtimeGrounded){runtimePlayerVY=-runtimeJumpStrength;runtimeGrounded=false;}jumpRequested=false;}
            pw=Math.max(20,player.optDouble("w",48));ph=Math.max(20,player.optDouble("h",60));oldBottom=runtimePlayerY+ph;
        }else jumpRequested=false;
        dt*=runtimeTimeScale;
        if(player!=null){
            runtimePlayerX=Math.max(0,Math.min(960-pw,runtimePlayerX+(moveAxis*runtimeMoveSpeed+runtimePlayerVX)*dt));
            runtimePlayerVY=Math.min(1200,runtimePlayerVY+runtimeGravity*dt);
            runtimePlayerY=Math.max(0,runtimePlayerY+runtimePlayerVY*dt);
            runtimeGrounded=false;
        }
        JSONArray all=runtimeSceneObjects();Set<String> currentContacts=new HashSet<>();
        for(int i=0;i<all.length();i++){
            JSONObject o=all.optJSONObject(i);if(o==null||o==player)continue;
            String type=o.optString("type","").toLowerCase(Locale.ROOT),name=o.optString("name","Object");
            if(hiddenRuntimeObjects.contains(name)||destroyedRuntimeObjects.contains(name))continue;
            Double followSpeed=runtimeFollowSpeed.get(name);if(followSpeed!=null){if(player!=null){double dx=runtimePlayerX-runtimeObjectCoordinate(o,true),dy=runtimePlayerY-runtimeObjectCoordinate(o,false),distance=Math.sqrt(dx*dx+dy*dy);if(distance>1){runtimeObjectVX.put(name,dx/distance*followSpeed);runtimeObjectVY.put(name,dy/distance*followSpeed);}else{runtimeObjectVX.put(name,0d);runtimeObjectVY.put(name,0d);}}else{runtimeObjectVX.put(name,0d);runtimeObjectVY.put(name,0d);}}
            runtimeObjectX.put(name,runtimeObjectCoordinate(o,true)+runtimeObjectVelocity(o,true)*dt);runtimeObjectY.put(name,runtimeObjectCoordinate(o,false)+runtimeObjectVelocity(o,false)*dt);
            if(player!=null){
                double ox=runtimeObjectCoordinate(o,true),oy=runtimeObjectCoordinate(o,false),ow=Math.max(1,o.optDouble("w")),oh=Math.max(1,o.optDouble("h"));
                boolean overlap=runtimePlayerX<ox+ow&&runtimePlayerX+pw>ox&&runtimePlayerY<oy+oh&&runtimePlayerY+ph>oy;
                boolean landedOnThisPlatform=type.equals("platform")&&runtimePlayerVY>=0&&oldBottom<=oy+12&&runtimePlayerY+ph>=oy&&runtimePlayerX+pw>ox&&runtimePlayerX<ox+ow;
                if(landedOnThisPlatform){runtimePlayerY=oy-ph;runtimePlayerVY=0;runtimeGrounded=true;}
                if(overlap&&!type.equals("camera")&&!type.equals("light")&&!type.equals("player")){String key=type+":"+name;currentContacts.add(key);if(!previousContacts.contains(key))executePhase("collision",type);}
            }
        }
        for(int i=0;i<all.length();i++){JSONObject a=all.optJSONObject(i);if(a==null)continue;String nameA=a.optString("name","Object"),typeA=a.optString("type","").toLowerCase(Locale.ROOT);if(typeA.equals("player")||typeA.equals("camera")||typeA.equals("light")||hiddenRuntimeObjects.contains(nameA)||destroyedRuntimeObjects.contains(nameA))continue;double ax=runtimeObjectCoordinate(a,true),ay=runtimeObjectCoordinate(a,false),aw=Math.max(1,a.optDouble("w",1)),ah=Math.max(1,a.optDouble("h",1));for(int j=i+1;j<all.length();j++){JSONObject b=all.optJSONObject(j);if(b==null)continue;String nameB=b.optString("name","Object"),typeB=b.optString("type","").toLowerCase(Locale.ROOT);if(typeB.equals("player")||typeB.equals("camera")||typeB.equals("light")||hiddenRuntimeObjects.contains(nameB)||destroyedRuntimeObjects.contains(nameB))continue;double bx=runtimeObjectCoordinate(b,true),by=runtimeObjectCoordinate(b,false),bw=Math.max(1,b.optDouble("w",1)),bh=Math.max(1,b.optDouble("h",1));boolean overlap=ax<bx+bw&&ax+aw>bx&&ay<by+bh&&ay+ah>by;if(overlap){String key="pair:"+nameA+":"+nameB;currentContacts.add(key);if(!previousContacts.contains(key)){lastObjectCollisionA=nameA;lastObjectCollisionB=nameB;lastObjectCollisionTypeA=typeA;lastObjectCollisionTypeB=typeB;executePhase("objectCollision",typeA+"|"+typeB);if(destroyedRuntimeObjects.contains(nameA)||destroyedRuntimeObjects.contains(nameB))break;}}}}
        cleanupRuntimeSpawns();
        // Platforms handle landing above; do not force an invisible floor, so the player can fall off-screen.
        previousContacts.clear();previousContacts.addAll(currentContacts);
        runtimeScore=(int)Math.round(runtimeVars.getOrDefault("Score",0d));runtimeLives=(int)Math.round(runtimeVars.getOrDefault("Lives",3d));
        JSONObject activePlayer=findPlayer();if(runtimeLives<=0||(activePlayer!=null&&(runtimeObjectHealthValue(activePlayer)<=0||destroyedRuntimeObjects.contains(activePlayer.optString("name","Player"))))){runtimeGameOver=true;moveAxis=0;runtimePlayerVX=0;runtimePlayerVY=0;jumpRequested=false;}
    }
    Button holdButton(String text,int direction){
        Button b=btn(text,()->{});b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){moveAxis=direction;return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){if(moveAxis==direction)moveAxis=0;return true;}return true;});return b;
    }
    JSONObject read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);}return new JSONObject(b.toString("UTF-8"));}
    void open(File f){try{project=read(f);activeFile=f;tab=project.optString("dimension","2D");preview=false;selected=0;editor();}catch(Exception e){toast("Không mở được dự án");}}
    void save(){try(FileOutputStream out=new FileOutputStream(activeFile)){out.write(project.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception e){toast("Lỗi lưu");}}
    void validateProject(){
        ArrayList<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        if(project==null){toast("Chưa mở dự án");return;}
        String projectName=project.optString("name","").trim();
        if(projectName.isEmpty())errors.add("• Dự án chưa có tên.");
        String dimension=project.optString("dimension","").trim();
        if(!dimension.equalsIgnoreCase("2D")&&!dimension.equalsIgnoreCase("3D"))errors.add("• Loại cảnh phải là 2D hoặc 3D.");
        int width=project.optInt("width",0),height=project.optInt("height",0);
        if(width<=0||height<=0)errors.add("• Kích thước cảnh phải lớn hơn 0.");
        JSONArray os=objects();HashSet<String> names=new HashSet<>();boolean hasPlayer=false;
        for(int i=0;i<os.length();i++){
            JSONObject o=os.optJSONObject(i);if(o==null){errors.add("• Đối tượng thứ "+(i+1)+" không hợp lệ.");continue;}
            String name=o.optString("name","").trim();
            if(name.isEmpty())errors.add("• Đối tượng thứ "+(i+1)+" chưa có tên.");
            else if(!names.add(name.toLowerCase(Locale.ROOT)))errors.add("• Tên đối tượng bị trùng: "+name+" (các lệnh sẽ khó xác định đúng đối tượng).");
            if(isPlayerObject(o))hasPlayer=true;
            String objectLabel=name.isEmpty()?"đối tượng "+(i+1):name;
            double x=o.optDouble("x",Double.NaN),y=o.optDouble("y",Double.NaN),w=o.optDouble("w",Double.NaN),h=o.optDouble("h",Double.NaN);
            double health=o.optDouble("health",100),vx=o.optDouble("vx",0),vy=o.optDouble("vy",0);
            if(!finiteNumber(x)||!finiteNumber(y)||!finiteNumber(w)||!finiteNumber(h))errors.add("• Tọa độ/kích thước của "+objectLabel+" phải là số hữu hạn.");
            if(w<0||h<0)errors.add("• Kích thước của "+objectLabel+" không được âm.");
            if(!finiteNumber(health)||health<0)errors.add("• Máu ban đầu của "+objectLabel+" phải là số không âm.");
            if(!finiteNumber(vx)||!finiteNumber(vy))errors.add("• Vận tốc ban đầu của "+objectLabel+" phải là số hữu hạn.");
        }
        if(!hasPlayer)warnings.add("• Chưa có Player; các sự kiện điều khiển nhân vật sẽ không hoạt động.");
        JSONArray vars=project.optJSONArray("variables");HashSet<String> varNames=new HashSet<>();
        if(vars!=null)for(int i=0;i<vars.length();i++){
            JSONObject v=vars.optJSONObject(i);if(v==null){errors.add("• Biến thứ "+(i+1)+" không hợp lệ.");continue;}
            String name=v.optString("name","").trim();
            double value=v.optDouble("value",Double.NaN);
            if(!name.matches("[A-Za-z_][A-Za-z0-9_]*"))errors.add("• Tên biến không hợp lệ: "+(name.isEmpty()?"(trống)":name)+".");
            else if(!varNames.add(name.toLowerCase(Locale.ROOT)))errors.add("• Tên biến bị trùng: "+name+".");
            if(!finiteNumber(value))errors.add("• Giá trị ban đầu của biến "+(name.isEmpty()?"(không tên)":name)+" phải là số hữu hạn.");
        }
        JSONArray events=project.optJSONArray("events");
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i);if(e==null){errors.add("• Khối sự kiện thứ "+(i+1)+" không hợp lệ.");continue;}
            if(e.optString("trigger","").trim().isEmpty())errors.add("• Khối sự kiện "+(i+1)+" thiếu điều kiện kích hoạt.");
            if(e.optString("action","").trim().isEmpty()&&e.optString("customAction","").trim().isEmpty())warnings.add("• Khối sự kiện "+(i+1)+" chưa có hành động.");
        }
        StringBuilder report=new StringBuilder();
        if(errors.isEmpty())report.append("✓ Không tìm thấy lỗi cấu trúc cơ bản.\n");
        else{report.append("LỖI CẦN SỬA (").append(errors.size()).append("):\n");for(String e:errors)report.append(e).append("\n");}
        if(!warnings.isEmpty()){report.append("\nLƯU Ý (").append(warnings.size()).append("):\n");for(String w:warnings)report.append(w).append("\n");}
        report.append("\nKiểm tra này rà soát cấu trúc dự án, không thay thế việc chạy thử game trên thiết bị.");
        new AlertDialog.Builder(this).setTitle(errors.isEmpty()?"Kiểm tra dự án":"Phát hiện vấn đề").setMessage(report.toString()).setPositiveButton("Đóng",null).show();
    }

    JSONArray objects(){JSONArray a=project.optJSONArray("objects");if(a==null){a=new JSONArray();try{project.put("objects",a);}catch(Exception ignored){}}return a;}
    void add(String n,String type,int color){try{JSONArray a=objects();a.put(obj(n+" "+(a.length()+1),type,220+(a.length()*47)%540,180+(a.length()*31)%230,type.equals("coin")?30:54,type.equals("coin")?30:54,color));selected=a.length()-1;save();editor();}catch(Exception ignored){}}

    void editor(){
        LinearLayout root=col();LinearLayout top=row();top.addView(btn("‹",this::home));
        top.addView(label(project.optString("name","Game"),16,fg,true),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(btn("Lưu",()->{save();toast("Đã lưu ngoại tuyến");}));
        top.addView(btn(preview?"Dừng":"Chạy",()->{if(!preview)startRuntime();else stopRuntime();preview=!preview;editor();}));root.addView(top);
        LinearLayout tabs=row();for(String t:new String[]{"2D","3D","Đối tượng","Sự kiện","Biến"})tabs.addView(btn(t,()->{tab=t;editor();}));root.addView(scroll(tabs));\n        root.addView(btn("✓ Kiểm tra dự án",this::validateProject));
        if(preview){
            boolean preview3D=project.optString("dimension","2D").equals("3D");
            root.addView(label("CHẠY THỬ • Sự kiện đang được thực thi",12,Color.GREEN,true));
            root.addView(new SceneView(true,preview3D),new LinearLayout.LayoutParams(-1,dp(330)));
            if(!preview3D&&findPlayer()!=null){LinearLayout controls=row();controls.addView(holdButton("◀", -1));controls.addView(btn("NHẢY",()->{if(!runtimeGameOver)jumpRequested=true;}));controls.addView(btn("BẮN",()->{if(!runtimeGameOver)executePhase("shoot",null);}));controls.addView(holdButton("▶", 1));root.addView(controls);}
            root.addView(label("Điểm: "+runtimeScore+"     Mạng: "+runtimeLives+(findPlayer()!=null?"     Máu: "+(int)Math.round(runtimeObjectHealthValue(findPlayer())):""),16,fg,true));
            if(runtimeGameOver)root.addView(label("GAME OVER — Nhấn Chơi lại để bắt đầu lại",18,Color.RED,true));
            root.addView(btn("Chơi lại",()->{stopRuntime();startRuntime();editor();}));
            root.addView(btn("Quay lại chỉnh sửa",()->{stopRuntime();preview=false;editor();}));
        }
        else if(tab.equals("2D")||tab.equals("3D")){
            boolean three=tab.equals("3D");root.addView(label(three?"Cảnh 3D • khối cơ bản":"Cảnh 2D • kéo đối tượng để di chuyển",13,muted,true));
            root.addView(new SceneView(false,three),new LinearLayout.LayoutParams(-1,dp(320)));
            LinearLayout tools=row();tools.addView(btn("+ Player",()->add("Player","player",Color.BLUE)));tools.addView(btn("+ Enemy",()->add("Enemy","enemy",Color.RED)));tools.addView(btn("+ Khối",()->add("Cube","cube",Color.rgb(110,130,255))));tools.addView(btn("+ Coin",()->add("Coin","coin",Color.YELLOW)));tools.addView(btn("+ Nền",()->add("Platform","platform",Color.GREEN)));root.addView(scroll(tools));root.addView(btn("+ Thêm mẫu Đạn (Projectile)",()->add("Projectile","projectile",Color.YELLOW)));
            root.addView(label("Đang chọn: "+selectedName(),14,fg,true));root.addView(btn("Sửa thuộc tính",this::editObject));root.addView(btn("Xuất dự án ZIP",this::exportZip));root.addView(btn("Xuất APK",this::apkNotice));
        }else if(tab.equals("Đối tượng"))objectPanel(root);else if(tab.equals("Sự kiện"))eventPanel(root);else variablePanel(root);
        setContentView(scroll(root));
    }
    String selectedName(){JSONArray a=objects();if(a.length()==0)return "(không có)";selected=Math.max(0,Math.min(selected,a.length()-1));return a.optJSONObject(selected).optString("name","Object");}
    void objectPanel(LinearLayout root){
        root.addView(label("Đối tượng trong cảnh",18,fg,true));JSONArray a=objects();
        for(int i=0;i<a.length();i++){int idx=i;JSONObject o=a.optJSONObject(i);TextView t=card((i==selected?"● ":"○ ")+o.optString("name")+"  •  "+o.optString("type")+"\nX="+(int)o.optDouble("x")+"  Y="+(int)o.optDouble("y")+"\nVX="+o.optDouble("vx",0)+"  VY="+o.optDouble("vy",0)+"  HP="+o.optDouble("health",100));root.addView(t);t.setOnClickListener(v->{selected=idx;editor();});}
        root.addView(btn("+ Thêm đối tượng",()->new AlertDialog.Builder(this).setTitle("Loại đối tượng").setItems(new String[]{"Nhân vật","Enemy","Khối 3D","Coin","Nền sàn","Đạn (Projectile)"},(d,w)->{if(w==0)add("Player","player",Color.BLUE);if(w==1)add("Enemy","enemy",Color.RED);if(w==2)add("Cube","cube",Color.rgb(110,130,255));if(w==3)add("Coin","coin",Color.YELLOW);if(w==4)add("Platform","platform",Color.GREEN);if(w==5)add("Projectile","projectile",Color.YELLOW);}).show()));
        root.addView(btn("Gán ảnh từ thư viện",this::importImage));
        root.addView(btn("Sửa thuộc tính",this::editObject));
        root.addView(btn("Nhân bản đối tượng",this::duplicateSelected));
        root.addView(btn("Xóa đối tượng",()->{if(objects().length()>0){objects().remove(selected);selected=Math.max(0,selected-1);save();editor();}}));
    }
    EditText field(String s){EditText e=new EditText(this);e.setSingleLine(true);e.setText(s);e.setTextColor(fg);e.setBackgroundColor(panel);return e;}
    void editObject(){
        JSONArray a=objects();if(a.length()==0)return;selected=Math.max(0,Math.min(selected,a.length()-1));JSONObject o=a.optJSONObject(selected);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(10),dp(6),dp(10),dp(6));box.setOrientation(1);
        EditText n=field(o.optString("name"));EditText x=field(""+(int)o.optDouble("x"));EditText y=field(""+(int)o.optDouble("y"));EditText w=field(""+(int)o.optDouble("w"));EditText h=field(""+(int)o.optDouble("h"));EditText health=field(""+(int)o.optDouble("health",100));EditText vx=field(""+o.optDouble("vx",0));EditText vy=field(""+o.optDouble("vy",0));
        String[] types={"player","enemy","cube","coin","platform","projectile"};
        String[] typeLabels={"Nhân vật (Player)","Kẻ địch (Enemy)","Khối 3D (Cube)","Vật phẩm (Coin)","Nền sàn (Platform)","Đạn (Projectile)"};
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
        for(Object[] pair:new Object[][]{{"Tên",n},{"Loại đối tượng",type},{"Màu",color},{"Vị trí X",x},{"Vị trí Y",y},{"Chiều rộng",w},{"Chiều cao",h},{"Máu ban đầu (Health)",health},{"Vận tốc X ban đầu (đơn vị/giây)",vx},{"Vận tốc Y ban đầu (đơn vị/giây)",vy}}){box.addView(label((String)pair[0],12,muted,false));box.addView((View)pair[1]);}
        new AlertDialog.Builder(this).setTitle("Thuộc tính đối tượng").setView(box).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,z)->{try{o.put("name",n.getText().toString().trim().isEmpty()?currentType:n.getText().toString().trim());o.put("type",types[type.getSelectedItemPosition()]);o.put("color",colors[color.getSelectedItemPosition()]);o.put("x",num(x));o.put("y",num(y));o.put("w",Math.max(1,num(w)));o.put("h",Math.max(1,num(h)));o.put("health",Math.max(0,num(health)));o.put("vx",num(vx));o.put("vy",num(vy));spriteCache.clear();save();editor();}catch(Exception ex){toast("Không lưu được thuộc tính");}}).show();
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
    JSONArray projectVariables(){
        JSONArray a=project.optJSONArray("variables");
        if(a==null){a=new JSONArray();try{project.put("variables",a);}catch(Exception ignored){}}
        return a;
    }
    void variablePanel(LinearLayout root){
        root.addView(label("Biến toàn cục",18,fg,true));
        root.addView(label("Dùng biến để lưu điểm, máu, tiền, cấp độ, bộ đếm và trạng thái. Biến được dùng trong điều kiện sự kiện và lệnh tùy chỉnh.",12,muted,false));
        JSONArray a=projectVariables();
        for(int i=0;i<a.length();i++){
            JSONObject v=a.optJSONObject(i);if(v==null)continue;int index=i;
            TextView card=card(v.optString("name","Variable")+" = "+v.optString("value","0")+"\nChạm để sửa • Nhấn giữ để xóa");
            root.addView(card);
            card.setOnClickListener(view->editVariable(index));
            card.setOnLongClickListener(view->{new AlertDialog.Builder(this).setMessage("Xóa biến "+v.optString("name")+"?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(d,w)->{projectVariables().remove(index);save();editor();}).show();return true;});
        }
        root.addView(btn("+ Tạo biến",()->editVariable(-1)));
        root.addView(label("Ví dụ: tạo EnemyHP = 100; điều kiện EnemyHP <= 0; lệnh add Money 5, set Score = Score + 10, set EnemyHP = EnemyHP - 25. Hỗ trợ +, -, *, /, %, dấu ngoặc và các biến đã tạo.",12,muted,false));
    }
    void editVariable(int index){
        JSONArray a=projectVariables();
        JSONObject old=index>=0&&index<a.length()?a.optJSONObject(index):null;
        EditText name=field(old==null?"":old.optString("name",""));
        EditText value=field(old==null?"0":Double.toString(old.optDouble("value",0)));
        name.setHint("Tên biến, ví dụ EnemyHP");
        value.setHint("Giá trị số ban đầu");
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        box.addView(label("Tên biến (chữ, số, dấu gạch dưới; không bắt đầu bằng số)",12,muted,false));box.addView(name);
        box.addView(label("Giá trị ban đầu",12,muted,false));box.addView(value);
        new AlertDialog.Builder(this).setTitle(old==null?"Tạo biến":"Sửa biến").setView(box).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{
            String n=name.getText().toString().trim();double v;
            try{v=Double.parseDouble(value.getText().toString().trim());}catch(Exception ex){toast("Giá trị phải là số");return;}
            if(!n.matches("[A-Za-z_][A-Za-z0-9_]*")){toast("Tên biến không hợp lệ");return;}
            if(n.equalsIgnoreCase("Player")||n.equalsIgnoreCase("Gravity")||n.equalsIgnoreCase("MoveSpeed")||n.equalsIgnoreCase("JumpStrength")||n.equalsIgnoreCase("TimeScale")){toast("Tên này dành cho thuộc tính hệ thống");return;}
            for(int j=0;j<a.length();j++){if(j==index)continue;JSONObject other=a.optJSONObject(j);if(other!=null&&other.optString("name","").equalsIgnoreCase(n)){toast("Tên biến đã tồn tại");return;}}
            try{JSONObject saved=old==null?new JSONObject():old;saved.put("name",n);saved.put("value",v);if(old==null)a.put(saved);save();editor();toast("Đã lưu biến "+n);}catch(Exception ex){toast("Không lưu được biến");}
        }).show();
    }

    void eventPanel(LinearLayout root){
        root.addView(label("Khối sự kiện No-code",18,fg,true));
        root.addView(label("Chạm vào một khối để sửa điều kiện/hành động. Nhấn giữ để xóa.",12,muted,false));
        JSONArray a=project.optJSONArray("events");
        if(a!=null)for(int i=0;i<a.length();i++){
            int idx=i;JSONObject e=a.optJSONObject(i);
            String shownAction=e.optString("customAction","").trim();if(shownAction.isEmpty())shownAction=e.optString("action","");
            String cond=e.optString("condition","").trim();
            TextView t=card("🟩 KHI "+e.optString("trigger")+(cond.isEmpty()?"":"\n🔎 NẾU "+cond)+"\n🟦 LÀM "+shownAction);
            root.addView(t);t.setOnClickListener(v->editEvent(idx));
            t.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("Xóa khối này?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(d,w)->{project.optJSONArray("events").remove(idx);save();editor();}).show();return true;});
        }
        root.addView(btn("+ Thêm sự kiện",()->editEvent(-1)));
        root.addView(label("Điều kiện hỗ trợ: Score >= 10, Lives <= 0, Timer > 5, Player.X >= 300; kết hợp &&, ||, ! và dấu ngoặc. Chọn 'Khi điều kiện chuyển thành đúng' để chạy một lần khi điều kiện vừa đúng.",12,muted,false));
        root.addView(label("Lệnh tùy chỉnh: thêm mẫu Đạn (Projectile), rồi dùng spawn Projectile at Player 260 0 (tạo bản sao đạn tại Player, vận tốc X=260/Y=0); spawn Projectile 300 200 260 0; follow Enemy Player 100; stopFollow Enemy; set Enemy.VX = 80; set Enemy.VY = 0; set Player.VX = 100; set Enemy.X = Player.X + 80; teleport Enemy 400 200; set LastHitEnemy.Health = LastHitEnemy.Health - 25; destroy LastHitProjectile (nhắm đúng đạn vừa va chạm); destroy Enemy. Spawn chỉ tồn tại trong lúc chơi thử, không sửa dự án gốc. VX/VY là vận tốc X/Y theo giây. Tên mẫu dùng trong lệnh spawn nên viết liền, không dấu cách. Các lệnh khác: set Score = 10; add Score 5; subtract Lives 1; set Gravity = 800; set MoveSpeed = 300; set JumpStrength = 500; set TimeScale = 0.5; hide Coin; show Enemy; respawn Player.",12,muted,false));
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
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.rgb(20,33,51));if(run)tickRuntime();float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return;c.save();c.translate((getWidth()-960*scale)/2,(getHeight()-540*scale)/2);c.scale(scale,scale);if(three)draw3(c);else draw2(c);c.restore();p.setColor(muted);p.setTextSize(18);c.drawText(run?(three?"CHẠY THỬ 3D":"CHẠY THỬ 2D"):(three?"3D VIEW":"2D SCENE"),12,24,p);if(run){p.setColor(Color.WHITE);p.setTextSize(20);String extraHud="";if(runtimeVars.containsKey("Gold"))extraHud+="   Gold: "+(int)Math.round(runtimeVars.getOrDefault("Gold",0d));if(runtimeVars.containsKey("Level"))extraHud+="   Level: "+(int)Math.round(runtimeVars.getOrDefault("Level",1d));c.drawText("Score: "+runtimeScore+"   Lives: "+runtimeLives+(findPlayer()!=null?"   HP: "+(int)Math.round(runtimeObjectHealthValue(findPlayer())):"")+extraHud,12,52,p);if(runtimeGameOver){p.setColor(Color.RED);p.setTextSize(38);c.drawText("GAME OVER",Math.max(12,getWidth()/2f-105),getHeight()/2f,p);}postInvalidateDelayed(40);}}
        void draw2(Canvas c){p.setColor(Color.rgb(100,180,242));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(129,201,252));for(int x=0;x<=960;x+=40)c.drawLine(x,0,x,540,p);for(int y=0;y<=540;y+=40)c.drawLine(0,y,960,y,p);JSONArray a=run?runtimeSceneObjects():objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||(run&&(hiddenRuntimeObjects.contains(o.optString("name"))||destroyedRuntimeObjects.contains(o.optString("name")))))continue;float x=(float)(run?runtimeObjectCoordinate(o,true):o.optDouble("x")),y=(float)(run?runtimeObjectCoordinate(o,false):o.optDouble("y")),w=(float)o.optDouble("w"),h=(float)o.optDouble("h");String type=o.optString("type");p.setColor(o.optInt("color",Color.WHITE));Bitmap sprite=loadSprite(o.optString("image",""));if(sprite!=null)c.drawBitmap(sprite,null,new RectF(x,y,x+Math.max(1,w),y+Math.max(1,h)),p);else if(type.equals("coin")||type.equals("projectile"))c.drawCircle(x+w/2,y+h/2,Math.max(w,h)/2,p);else if(type.equals("platform")){c.drawRect(x,y,x+Math.max(40,w),y+Math.max(20,h),p);p.setColor(Color.rgb(117,73,45));c.drawRect(x,y+h*.6f,x+Math.max(40,w),y+Math.max(20,h),p);}else if(type.equals("camera")||type.equals("light")){p.setTextSize(25);c.drawText(type.equals("camera")?"CAM":"LGT",x+8,y+32,p);}else{c.drawRoundRect(x,y,x+Math.max(34,w),y+Math.max(34,h),8,8,p);p.setColor(Color.WHITE);p.setTextSize(13);c.drawText(o.optString("name"),x,y-4,p);}if(run&&(type.equals("enemy")||type.equals("player")))drawRuntimeHealthBar(c,o,x,y,Math.max(34,w));if(!run&&i==selected){p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRect(x-4,y-4,x+Math.max(34,w)+4,y+Math.max(34,h)+4,p);p.setStyle(Paint.Style.FILL);}}}
        void drawRuntimeHealthBar(Canvas c,JSONObject o,float x,float y,float w){double max=Math.max(1,o.optDouble("health",100)),hp=Math.max(0,Math.min(max,runtimeObjectHealthValue(o)));float top=y-9;p.setColor(Color.rgb(60,30,36));c.drawRect(x,top,x+w,top+5,p);p.setColor(hp/max>.35?Color.rgb(45,210,90):Color.rgb(245,75,70));c.drawRect(x,top,x+(float)(w*hp/max),top+5,p);}
     void draw3(Canvas c){p.setColor(Color.rgb(34,51,73));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(65,87,111));for(int i=-800;i<1600;i+=50){c.drawLine(480+i,0,480+i*.45f,540,p);c.drawLine(0,270+i*.24f,960,270+i*.24f,p);}JSONArray a=run?runtimeSceneObjects():objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||(run&&(hiddenRuntimeObjects.contains(o.optString("name"))||destroyedRuntimeObjects.contains(o.optString("name")))))continue;String type=o.optString("type");p.setColor(o.optInt("color",Color.BLUE));if(type.equals("camera")||type.equals("light")){p.setTextSize(22);c.drawText(type.equals("camera")?"◉ Camera":"☼ Light",35+i*20,55+i*24,p);continue;}float x=(float)(run?runtimeObjectCoordinate(o,true):o.optDouble("x")),y=(float)(run?runtimeObjectCoordinate(o,false):o.optDouble("y")),s=Math.max(30,(float)o.optDouble("w",60));x=480+(x-480)*.72f-(y-270)*.35f;y=280+(y-270)*.48f+(x-480)*.18f;Path path=new Path();path.moveTo(x,y-s);path.lineTo(x+s/2,y-s+s/3);path.lineTo(x,y-s+2*s/3);path.lineTo(x-s/2,y-s+s/3);path.close();p.setColor(o.optInt("color",Color.BLUE));c.drawPath(path,p);p.setColor(Color.rgb(35,74,138));c.drawRect(x-s/2,y-s+s/3,x,y+s/3,p);p.setColor(Color.rgb(27,57,111));c.drawRect(x,y-s+2*s/3,x+s/2,y+s/3,p);if(run&&(type.equals("enemy")||type.equals("player")))drawRuntimeHealthBar(c,o,x-s/2,y-s,s);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getX();dy=e.getY();if(run){jumpRequested=true;invalidate();return true;}JSONArray a=objects();double best=Double.MAX_VALUE;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);double ox=(dx-getWidth()/2f+480*scale)/scale,oy=(dy-getHeight()/2f+270*scale)/scale;double dd=Math.pow(ox-o.optDouble("x")-o.optDouble("w")/2,2)+Math.pow(oy-o.optDouble("y")-o.optDouble("h")/2,2);if(dd<best){best=dd;selected=i;}}if(selected<a.length()){JSONObject o=a.optJSONObject(selected);sx=o.optDouble("x");sy=o.optDouble("y");drag=true;}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&drag){JSONObject o=objects().optJSONObject(selected);try{o.put("x",Math.max(0,Math.min(900,sx+(e.getX()-dx)/scale)));o.put("y",Math.max(0,Math.min(500,sy+(e.getY()-dy)/scale)));}catch(Exception ignored){}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(drag)save();drag=false;return true;}return true;}
    }
}
