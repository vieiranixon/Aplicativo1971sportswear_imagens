package com.nvn.baixador

import android.app.*
import android.os.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import okhttp3.*
import org.jsoup.Jsoup
import java.io.*
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

data class Product(val name:String,val page:String,val image:String,var selected:Boolean=true)

class MainActivity:Activity(){
 private val client=OkHttpClient()
 private val scope=Handler(Looper.getMainLooper())
 private val products=mutableListOf<Product>()
 private lateinit var status:TextView; private lateinit var list:LinearLayout
 private lateinit var download:Button; private lateinit var zip:Button
 private val out by lazy { File(getExternalFilesDir(null),"capas_baixadas").apply{mkdirs()} }

 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
  val url=findViewById<EditText>(R.id.url); val analyze=findViewById<Button>(R.id.analisar)
  download=findViewById(R.id.baixar);zip=findViewById(R.id.zip);status=findViewById(R.id.status);list=findViewById(R.id.lista)
  analyze.setOnClickListener{val u=url.text.toString().trim();if(u.isBlank()){status.text="Cole uma URL.";return@setOnClickListener}
   analyze.isEnabled=false;status.text="Analisando...";Thread{val r=collect(u);runOnUiThread{products.clear();products.addAll(r);render();status.text="${r.size} produtos encontrados.";analyze.isEnabled=true;download.isEnabled=r.isNotEmpty()}}.start()}
  download.setOnClickListener{downloadSelected()}
  zip.setOnClickListener{makeZip()}
 }
 private fun get(u:String):String{val r=Request.Builder().url(u).header("User-Agent","Mozilla/5.0").build();return client.newCall(r).execute().use{it.body?.string()?:""}}
 private fun collect(start:String):List<Product>{
  val base=Jsoup.parse(get(start),start);val host=URI(start).host?:return emptyList();val links=LinkedHashSet<String>()
  for(a in base.select("a[href]")){val h=a.absUrl("href");if(h.isBlank())continue
   if(URI(h).host==host && (h.contains("/futebol/")||h.contains("/camisa-")||h.contains("/produto"))) links.add(h)}
  val outp=mutableListOf<Product>()
  for(p in links){try{val d=Jsoup.parse(get(p),p);val n=d.select("meta[property=og:title]").attr("content").ifBlank{d.title()};val im=d.select("meta[property=og:image]").attr("content").ifBlank{d.select("img[src]").firstOrNull()?.absUrl("src")?:""};if(im.isNotBlank())outp.add(Product(n,p,im))}catch(_:Exception){}}
  return outp
 }
 private fun render(){list.removeAllViews();products.forEachIndexed{i,p->
  val cb=CheckBox(this);cb.text="${i+1}. ${p.name}";cb.isChecked=true;cb.setOnCheckedChangeListener{_,v->p.selected=v};list.addView(cb)}
 }
 private fun downloadSelected(){status.text="Baixando...";download.isEnabled=false;Thread{
  var ok=0;products.filter{it.selected}.forEachIndexed{i,p->try{val req=Request.Builder().url(p.image).header("User-Agent","Mozilla/5.0").build();val bytes=client.newCall(req).execute().use{it.body?.bytes()?:ByteArray(0)}
    val bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return@forEachIndexed
    val outBmp=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);val c=Canvas(outBmp);c.drawColor(Color.WHITE)
    val scale=min(900f/bmp.width,900f/bmp.height);val w=(bmp.width*scale).toInt();val h=(bmp.height*scale).toInt();val left=(1000-w)/2f;val top=(1000-h)/2f;c.drawBitmap(bmp,null,RectF(left,top,left+w,top+h),Paint(Paint.ANTI_ALIAS_FLAG))
    val f=File(out,String.format("%03d - %s.jpg",i+1,clean(p.name)));FileOutputStream(f).use{outBmp.compress(Bitmap.CompressFormat.JPEG,95,it)};ok++
  }catch(_:Exception){}}
  runOnUiThread{status.text="$ok capas salvas em ${out.absolutePath}";zip.isEnabled=ok>0;download.isEnabled=true}
 }.start()}
 private fun makeZip(){try{val z=File(getExternalFilesDir(null),"capas_1971sportswear.zip");ZipOutputStream(BufferedOutputStream(FileOutputStream(z))).use{zos->out.listFiles()?.filter{it.extension=="jpg"}?.forEach{f->zos.putNextEntry(ZipEntry(f.name));f.inputStream().use{it.copyTo(zos)};zos.closeEntry()}}
  status.text="ZIP criado: ${z.absolutePath}"}catch(e:Exception){status.text="Erro ao criar ZIP: ${e.message}"}}
 private fun clean(s:String)=s.replace(Regex("""[\\/:*?"<>|]"""),"").replace(Regex("\\s+")," ").trim().take(120).ifBlank{"produto"}
}