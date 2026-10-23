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
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class MainActivity extends Activity {
    int bg=Color.rgb(13,20,34), panel=Color.rgb(25,37,58), accent=Color.rgb(48,115,243);
    int fg=Color.rgb(239,244,255), muted=Color.rgb(160,178,204);
    JSONObject project; File activeFile; String tab="2D"; boolean preview=false; EditText nameBox;
    static final int EXPORT_ZIP=7;

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);home();}
    File dir(){File f=new File(getFilesDir(),"projects");f.mkdirs();return f;}
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
        root.addView(btn("Nhập dự án JSON",this::importJson));
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
    JSONObject read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);}return new JSONObject(b.toString("UTF-8"));}
    void open(File f){try{project=read(f);activeFile=f;tab=project.optString("dimension","2D");preview=false;selected=0;editor();}catch(Exception e){toast("Không mở được dự án");}}
    void save(){try(FileOutputStream out=new FileOutputStream(activeFile)){out.write(project.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception e){toast("Lỗi lưu");}}
    JSONArray objects(){JSONArray a=project.optJSONArray("objects");if(a==null){a=new JSONArray();try{project.put("objects",a);}catch(Exception ignored){}}return a;}
    void add(String n,String type,int color){try{JSONArray a=objects();a.put(obj(n+" "+(a.length()+1),type,220+(a.length()*47)%540,180+(a.length()*31)%230,type.equals("coin")?30:54,type.equals("coin")?30:54,color));selected=a.length()-1;save();editor();}catch(Exception ignored){}}

    void editor(){
        LinearLayout root=col();LinearLayout top=row();top.addView(btn("‹",this::home));
        top.addView(label(project.optString("name","Game"),16,fg,true),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(btn("Lưu",()->{save();toast("Đã lưu ngoại tuyến");}));
        top.addView(btn(preview?"Dừng":"Chạy",()->{preview=!preview;editor();}));root.addView(top);
        LinearLayout tabs=row();for(String t:new String[]{"2D","3D","Đối tượng","Sự kiện"})tabs.addView(btn(t,()->{tab=t;editor();}));root.addView(scroll(tabs));
        if(preview){root.addView(label("CHẠY THỬ — chạm vào màn hình để tăng điểm",12,Color.GREEN,true));root.addView(new SceneView(true,tab.equals("3D")),new LinearLayout.LayoutParams(-1,dp(330)));root.addView(btn("Quay lại chỉnh sửa",()->{preview=false;editor();}));}
        else if(tab.equals("2D")||tab.equals("3D")){
            boolean three=tab.equals("3D");root.addView(label(three?"Cảnh 3D • khối cơ bản":"Cảnh 2D • kéo đối tượng để di chuyển",13,muted,true));
            root.addView(new SceneView(false,three),new LinearLayout.LayoutParams(-1,dp(320)));
            LinearLayout tools=row();tools.addView(btn("+ Player",()->add("Player","player",Color.BLUE)));tools.addView(btn("+ Khối",()->add("Cube","cube",Color.rgb(110,130,255))));tools.addView(btn("+ Coin",()->add("Coin","coin",Color.YELLOW)));tools.addView(btn("+ Nền",()->add("Platform","platform",Color.GREEN)));root.addView(scroll(tools));
            root.addView(label("Đang chọn: "+selectedName(),14,fg,true));root.addView(btn("Sửa thuộc tính",this::editObject));root.addView(btn("Xuất dự án ZIP",this::exportZip));root.addView(btn("Xuất APK",this::apkNotice));
        }else if(tab.equals("Đối tượng"))objectPanel(root);else eventPanel(root);
        setContentView(scroll(root));
    }
    String selectedName(){JSONArray a=objects();if(a.length()==0)return "(không có)";selected=Math.max(0,Math.min(selected,a.length()-1));return a.optJSONObject(selected).optString("name","Object");}
    void objectPanel(LinearLayout root){
        root.addView(label("Đối tượng trong cảnh",18,fg,true));JSONArray a=objects();
        for(int i=0;i<a.length();i++){int idx=i;JSONObject o=a.optJSONObject(i);TextView t=card((i==selected?"● ":"○ ")+o.optString("name")+"  •  "+o.optString("type")+"\nX="+(int)o.optDouble("x")+"  Y="+(int)o.optDouble("y"));root.addView(t);t.setOnClickListener(v->{selected=idx;editor();});}
        root.addView(btn("+ Thêm đối tượng",()->new AlertDialog.Builder(this).setTitle("Loại đối tượng").setItems(new String[]{"Nhân vật","Khối 3D","Coin","Nền sàn"},(d,w)->{if(w==0)add("Player","player",Color.BLUE);if(w==1)add("Cube","cube",Color.rgb(110,130,255));if(w==2)add("Coin","coin",Color.YELLOW);if(w==3)add("Platform","platform",Color.GREEN);}).show()));
        root.addView(btn("Sửa thuộc tính",this::editObject));root.addView(btn("Xóa đối tượng",()->{if(objects().length()>0){objects().remove(selected);selected=Math.max(0,selected-1);save();editor();}}));
    }
    EditText field(String s){EditText e=new EditText(this);e.setSingleLine(true);e.setText(s);e.setTextColor(fg);e.setBackgroundColor(panel);return e;}
    void editObject(){
        JSONArray a=objects();if(a.length()==0)return;selected=Math.min(selected,a.length()-1);JSONObject o=a.optJSONObject(selected);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(10),dp(6),dp(10),dp(6));box.setOrientation(1);
        EditText n=field(o.optString("name"));EditText x=field(""+(int)o.optDouble("x"));EditText y=field(""+(int)o.optDouble("y"));EditText w=field(""+(int)o.optDouble("w"));EditText h=field(""+(int)o.optDouble("h"));
        for(Object[] pair:new Object[][]{{"Tên",n},{"X",x},{"Y",y},{"Rộng",w},{"Cao",h}}){box.addView(label((String)pair[0],12,muted,false));box.addView((View)pair[1]);}
        new AlertDialog.Builder(this).setTitle("Thuộc tính").setView(box).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,z)->{try{o.put("name",n.getText().toString());o.put("x",num(x));o.put("y",num(y));o.put("w",num(w));o.put("h",num(h));save();editor();}catch(Exception ignored){}}).show();
    }
    double num(EditText e){try{return Double.parseDouble(e.getText().toString());}catch(Exception ex){return 0;}}
    void eventPanel(LinearLayout root){
        root.addView(label("Khối sự kiện No-code",18,fg,true));root.addView(label("Điều kiện kích hoạt → Hành động",12,muted,false));
        JSONArray a=project.optJSONArray("events");if(a!=null)for(int i=0;i<a.length();i++){int idx=i;JSONObject e=a.optJSONObject(i);TextView t=card("🟩 KHI "+e.optString("trigger")+"\n🟦 LÀM "+e.optString("action"));root.addView(t);t.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("Xóa khối này?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(d,w)->{project.optJSONArray("events").remove(idx);save();editor();}).show();return true;});}
        root.addView(btn("+ Thêm sự kiện",()->{String[] presets={"Khi bắt đầu cảnh → Đặt Score = 0","Khi Player chạm Coin → Tăng Score thêm 1","Khi bấm nút Nhảy → Cho Player nhảy","Mỗi khung hình → Di chuyển Player","Khi Player chạm Enemy → Trừ 1 mạng"};new AlertDialog.Builder(this).setTitle("Chọn khối").setItems(presets,(d,i)->{try{String[] p=presets[i].split("→");project.getJSONArray("events").put(event(p[0].trim(),p[1].trim()));save();editor();}catch(Exception ignored){}}).show();}));
        root.addView(label("Các khối hiện được lưu trong dự án; trình thông dịch đầy đủ để thực thi tất cả sự kiện sẽ được phát triển tiếp.",12,muted,false));
    }
    void apkNotice(){new AlertDialog.Builder(this).setTitle("Xuất APK").setMessage("Chức năng đóng gói APK của game người dùng chưa được tích hợp ở bản nền tảng này. Hiện có thể lưu dự án ZIP ngoại tuyến. Bộ xuất APK trực tiếp là một cột mốc kỹ thuật tiếp theo, không phải tính năng đã hoàn thành.").setPositiveButton("Xuất ZIP", (d,w)->exportZip()).setNegativeButton("Đóng",null).show();}
    void exportZip(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,safe(project.optString("name","Game"))+".zip");try{startActivityForResult(i,EXPORT_ZIP);}catch(Exception e){toast("Không mở được trình chọn file");}}
    void importJson(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");try{startActivityForResult(i,EXPORT_ZIP+1);}catch(Exception e){toast("Không mở được trình chọn file");}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(req==EXPORT_ZIP){try(OutputStream raw=getContentResolver().openOutputStream(uri);ZipOutputStream z=new ZipOutputStream(raw)){save();z.putNextEntry(new ZipEntry("project.gsproj"));z.write(project.toString(2).getBytes(StandardCharsets.UTF_8));z.closeEntry();z.putNextEntry(new ZipEntry("README.txt"));z.write("GameStudio Offline project format v1".getBytes(StandardCharsets.UTF_8));z.closeEntry();}toast("Đã xuất dự án ZIP");}
        else{try(InputStream in=getContentResolver().openInputStream(uri)){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);project=new JSONObject(b.toString("UTF-8"));activeFile=new File(dir(),safe(project.optString("name","Imported"))+".gsproj");save();tab=project.optString("dimension","2D");editor();toast("Đã nhập dự án JSON");}}
    }catch(Exception e){toast("Không xử lý được file này");}}
    class SceneView extends View{
        Paint p=new Paint(3);boolean run,three;float dx,dy;double sx,sy;boolean drag;
        SceneView(boolean r,boolean d){super(MainActivity.this);run=r;three=d;setBackgroundColor(bg);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.rgb(20,33,51));float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return;c.save();c.translate((getWidth()-960*scale)/2,(getHeight()-540*scale)/2);c.scale(scale,scale);if(three)draw3(c);else draw2(c);c.restore();p.setColor(muted);p.setTextSize(18);c.drawText(run?"CHẠY THỬ":(three?"3D VIEW":"2D SCENE"),12,24,p);if(run)postInvalidateDelayed(40);}
        void draw2(Canvas c){p.setColor(Color.rgb(100,180,242));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(129,201,252));for(int x=0;x<=960;x+=40)c.drawLine(x,0,x,540,p);for(int y=0;y<=540;y+=40)c.drawLine(0,y,960,y,p);JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;float x=(float)o.optDouble("x"),y=(float)o.optDouble("y"),w=(float)o.optDouble("w"),h=(float)o.optDouble("h");String type=o.optString("type");if(run&&type.equals("player"))x=120+(System.currentTimeMillis()/16%700);p.setColor(o.optInt("color",Color.WHITE));if(type.equals("coin"))c.drawCircle(x+w/2,y+h/2,Math.max(w,h)/2,p);else if(type.equals("platform")){c.drawRect(x,y,x+Math.max(40,w),y+Math.max(20,h),p);p.setColor(Color.rgb(117,73,45));c.drawRect(x,y+h*.6f,x+Math.max(40,w),y+Math.max(20,h),p);}else if(type.equals("camera")||type.equals("light")){p.setTextSize(25);c.drawText(type.equals("camera")?"CAM":"LGT",x+8,y+32,p);}else{c.drawRoundRect(x,y,x+Math.max(34,w),y+Math.max(34,h),8,8,p);p.setColor(Color.WHITE);p.setTextSize(13);c.drawText(o.optString("name"),x,y-4,p);}if(!run&&i==selected){p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRect(x-4,y-4,x+Math.max(34,w)+4,y+Math.max(34,h)+4,p);p.setStyle(Paint.Style.FILL);}}}
        void draw3(Canvas c){p.setColor(Color.rgb(34,51,73));c.drawRect(0,0,960,540,p);p.setColor(Color.rgb(65,87,111));for(int i=-800;i<1600;i+=50){c.drawLine(480+i,0,480+i*.45f,540,p);c.drawLine(0,270+i*.24f,960,270+i*.24f,p);}JSONArray a=objects();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String type=o.optString("type");p.setColor(o.optInt("color",Color.BLUE));if(type.equals("camera")||type.equals("light")){p.setTextSize(22);c.drawText(type.equals("camera")?"◉ Camera":"☼ Light",35+i*20,55+i*24,p);continue;}float x=(float)o.optDouble("x"),y=(float)o.optDouble("y"),s=Math.max(30,(float)o.optDouble("w",60));x=480+(x-480)*.72f-(y-270)*.35f;y=280+(y-270)*.48f+(x-480)*.18f;Path path=new Path();path.moveTo(x,y-s);path.lineTo(x+s/2,y-s+s/3);path.lineTo(x,y-s+2*s/3);path.lineTo(x-s/2,y-s+s/3);path.close();p.setColor(o.optInt("color",Color.BLUE));c.drawPath(path,p);p.setColor(Color.rgb(35,74,138));c.drawRect(x-s/2,y-s+s/3,x,y+s/3,p);p.setColor(Color.rgb(27,57,111));c.drawRect(x,y-s+2*s/3,x+s/2,y+s/3,p);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){float scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getX();dy=e.getY();if(run){invalidate();return true;}JSONArray a=objects();double best=Double.MAX_VALUE;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);double ox=(dx-getWidth()/2f+480*scale)/scale,oy=(dy-getHeight()/2f+270*scale)/scale;double dd=Math.pow(ox-o.optDouble("x")-o.optDouble("w")/2,2)+Math.pow(oy-o.optDouble("y")-o.optDouble("h")/2,2);if(dd<best){best=dd;selected=i;}}if(selected<a.length()){JSONObject o=a.optJSONObject(selected);sx=o.optDouble("x");sy=o.optDouble("y");drag=true;}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&drag){JSONObject o=objects().optJSONObject(selected);try{o.put("x",Math.max(0,Math.min(900,sx+(e.getX()-dx)/scale)));o.put("y",Math.max(0,Math.min(500,sy+(e.getY()-dy)/scale)));}catch(Exception ignored){}invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(drag)save();drag=false;return true;}return true;}
    }
}
