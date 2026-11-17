package br.com.colheitaalfa

import android.app.*
import android.os.Bundle
import android.database.sqlite.*
import android.content.*
import android.graphics.Color
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class Db(c: Context): SQLiteOpenHelper(c,"colheita.db",null,1) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE owner (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
  db.execSQL("CREATE TABLE workers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
  db.execSQL("CREATE TABLE harvests (id INTEGER PRIMARY KEY AUTOINCREMENT, worker_id INTEGER NOT NULL, day TEXT NOT NULL, liters REAL NOT NULL, price REAL NOT NULL, paid INTEGER NOT NULL DEFAULT 0)")
 }
 override fun onUpgrade(db: SQLiteDatabase,oldVersion:Int,newVersion:Int) {}
 fun owner() = readableDatabase.rawQuery("SELECT name FROM owner LIMIT 1",null).use { if(it.moveToFirst()) it.getString(0) else null }
 fun setOwner(name:String) { writableDatabase.execSQL("INSERT INTO owner(id,name) VALUES(1,?)", arrayOf(name)) }
 fun workers(): List<Pair<Long,String>> { val a= mutableListOf<Pair<Long,String>>(); readableDatabase.rawQuery("SELECT id,name FROM workers ORDER BY name",null).use { while(it.moveToNext()) a.add(it.getLong(0) to it.getString(1)) }; return a }
 fun addWorker(n:String) { writableDatabase.execSQL("INSERT INTO workers(name) VALUES(?)",arrayOf(n)) }
 fun addHarvest(id:Long,day:String,liters:Double,price:Double) { writableDatabase.execSQL("INSERT INTO harvests(worker_id,day,liters,price) VALUES(?,?,?,?)",arrayOf(id,day,liters,price)) }
 fun report(start:String,end:String, unpaidOnly:Boolean=false):String {
  val sb=StringBuilder(); var totalL=0.0; var totalV=0.0
  val where=if(unpaidOnly) "AND h.paid=0" else ""
  readableDatabase.rawQuery("SELECT w.name,SUM(h.liters),SUM(h.liters*h.price) FROM harvests h JOIN workers w ON w.id=h.worker_id WHERE h.day>=? AND h.day<=? $where GROUP BY w.id ORDER BY w.name",arrayOf(start,end)).use { while(it.moveToNext()) { val l=it.getDouble(1); val v=it.getDouble(2); totalL+=l; totalV+=v; sb.append("${it.getString(0)}: ${fmt(l)} L | R$ ${fmt(v)}\n") } }
  return (if(sb.isEmpty()) "Nenhum registro no período.\n" else sb.toString())+"\nTOTAL: ${fmt(totalL)} L | R$ ${fmt(totalV)}"
 }
 fun pay(start:String,end:String) { writableDatabase.execSQL("UPDATE harvests SET paid=1 WHERE day>=? AND day<=? AND paid=0",arrayOf(start,end)) }
 companion object { fun fmt(v:Double)=String.format(Locale("pt","BR"),"%.2f",v) }
}

class MainActivity:Activity() {
 private lateinit var db:Db
 private lateinit var root:LinearLayout
 private fun field(hint:String,number:Boolean=false):EditText = EditText(this).apply { this.hint=hint; if(number) inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine(true) }
 private fun button(title:String,action:()->Unit) { root.addView(Button(this).apply { text=title; setOnClickListener { action() } }) }
 private fun text(s:String) { root.addView(TextView(this).apply { text=s; textSize=18f; setTextColor(Color.DKGRAY); setPadding(0,12,0,12) }) }
 private fun screen(title:String) { root.removeAllViews(); text("COLHEITA ALFA • $title") }
 private fun back()=button("Voltar ao início") { home() }
 private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
 private fun dates():Pair<EditText,EditText> { val a=field("Data inicial (AAAA-MM-DD)"); val b=field("Data final (AAAA-MM-DD)"); a.setText(today()); b.setText(today()); root.addView(a); root.addView(b); return a to b }
 private fun message(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=Db(this); root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }; val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll); if(db.owner()==null) setup() else home() }
 private fun setup() { screen("Cadastro do proprietário"); val n=field("Nome do proprietário"); root.addView(n); button("Cadastrar e começar") { if(n.text.isBlank()) message("Informe o nome") else { db.setOwner(n.text.toString().trim()); home() } } }
 private fun home() { screen("Olá, ${db.owner()}"); button("Cadastrar funcionário") { workers() }; button("Registrar colheita") { harvest() }; button("Relatório por período") { reports(false) }; button("Pagamentos") { reports(true) } }
 private fun workers() { screen("Funcionários"); db.workers().forEach { text(it.second) }; val n=field("Nome do funcionário"); root.addView(n); button("Salvar funcionário") { if(n.text.isBlank()) message("Informe o nome") else { db.addWorker(n.text.toString().trim()); workers() } }; back() }
 private fun harvest() { screen("Registrar colheita"); val ws=db.workers(); if(ws.isEmpty()) { text("Cadastre um funcionário primeiro."); back(); return }; val sp=Spinner(this); sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,ws.map { it.second }); root.addView(sp); val d=field("Data (AAAA-MM-DD)").apply { setText(today()) }; val l=field("Litros colhidos",true); val p=field("Preço por litro (R$)",true); root.addView(d); root.addView(l); root.addView(p); button("Salvar colheita") { val liters=l.text.toString().replace(',','.').toDoubleOrNull(); val price=p.text.toString().replace(',','.').toDoubleOrNull(); if(!validDate(d.text.toString())||liters==null||liters<=0||price==null||price<0) message("Confira data, litros e preço") else { db.addHarvest(ws[sp.selectedItemPosition].first,d.text.toString(),liters,price); message("Colheita registrada"); home() } }; back() }
 private fun validDate(s:String):Boolean { val f=SimpleDateFormat("yyyy-MM-dd",Locale.US); f.isLenient=false; return try { f.parse(s)?.let { f.format(it)==s } == true } catch(e:Exception) { false } }
 private fun reports(payment:Boolean) { screen(if(payment) "Pagamentos" else "Relatórios"); val (a,b)=dates(); val result=TextView(this).apply { textSize=17f; setPadding(0,20,0,20) }; root.addView(result); fun valid()=validDate(a.text.toString())&&validDate(b.text.toString())&&a.text.toString()<=b.text.toString(); button(if(payment) "Consultar valores pendentes" else "Gerar relatório") { if(!valid()) message("Confira as datas") else result.text=db.report(a.text.toString(),b.text.toString(),payment) }; if(payment) button("Marcar pagamentos do período como pagos") { if(!valid()) { message("Confira as datas") } else AlertDialog.Builder(this).setTitle("Confirmar pagamento").setMessage("Marcar TODAS as colheitas pendentes deste período como pagas? Os registros serão mantidos no histórico.").setNegativeButton("Cancelar",null).setPositiveButton("Confirmar") { _,_ -> db.pay(a.text.toString(),b.text.toString()); result.text=db.report(a.text.toString(),b.text.toString(),true); message("Pagamentos baixados") }.show() }; back() }
}
