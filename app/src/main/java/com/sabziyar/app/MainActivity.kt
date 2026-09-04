
package com.sabziyar.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Typeface
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

data class Customer(val id:Int, var name:String, var phone:String="")
data class OrderItem(val kind:String, var qty:Int)
data class Order(val id:Int, val customerId:Int, val date:String, val items:MutableList<OrderItem>, var invoiced:Boolean=false)

class MainActivity: Activity() {
    private val green = 0xFF218C4B.toInt()
    private val bg = 0xFFF5F7F5.toInt()
    private val prefs by lazy { getSharedPreferences("sabziyar", MODE_PRIVATE) }
    private val customers = mutableListOf<Customer>()
    private val orders = mutableListOf<Order>()
    private var nextId = 1
    private lateinit var content: LinearLayout

    override fun onCreate(b: Bundle?) { super.onCreate(b); load(); showHome() }

    private fun load() {
        val cs=prefs.getString("customers","") ?: ""
        if(cs.isNotBlank()) cs.split(";;").forEach { p-> val a=p.split("|"); if(a.size>=2) customers.add(Customer(a[0].toInt(),a[1],a.getOrNull(2)?:"")) }
        val os=prefs.getString("orders","") ?: ""
        if(os.isNotBlank()) os.split(";;").forEach { p->
            val a=p.split("#"); if(a.size>=4) {
                val items= mutableListOf<OrderItem>(); a[3].split(",").filter{it.isNotBlank()}.forEach{ q->val x=q.split(":"); if(x.size==2) items.add(OrderItem(x[0],x[1].toInt()))}
                orders.add(Order(a[0].toInt(),a[1].toInt(),a[2],items,a.getOrNull(4)=="1"))
            }
        }
        nextId=(customers.map{it.id}+orders.map{it.id}+listOf(0)).maxOrNull()!!+1
    }
    private fun save(){
        prefs.edit()
            .putString("customers",customers.joinToString(";;"){ "${it.id}|${it.name}|${it.phone}"})
            .putString("orders",orders.joinToString(";;"){ o-> "${o.id}#${o.customerId}#${o.date}#${o.items.joinToString(","){ "${it.kind}:${it.qty}" }}#${if(o.invoiced)"1" else "0"}"})
            .apply()
    }
    private fun base(title:String): LinearLayout {
        val root=LinearLayout(this); root.orientation=LinearLayout.VERTICAL; root.setBackgroundColor(bg)
        val head=TextView(this); head.text=title; head.setTextColor(0xFFFFFFFF.toInt()); head.textSize=21f; head.setTypeface(null,Typeface.BOLD); head.setPadding(24,35,24,35); head.setBackgroundColor(green)
        root.addView(head,LinearLayout.LayoutParams(-1,-2)); return root
    }
    private fun set(root:LinearLayout){ setContentView(root); content=root }
    private fun button(t:String, click:()->Unit):Button { val b=Button(this); b.text=t; b.setOnClickListener{click()}; return b }
    private fun label(t:String):TextView { val x=TextView(this); x.text=t; x.textSize=14f; x.setPadding(16,12,16,6); return x }
    private fun showHome(){
        val r=base("🥬 سبزی‌یار")
        r.addView(label("ثبت سفارش بسته‌ای: خوردن، خورشی، ماهی"))
        r.addView(button("📝 سفارش جدید"){newOrder()})
        r.addView(button("👥 مشتری‌ها"){customerPage()})
        r.addView(button("🧾 فاکتورها"){invoicePage()})
        r.addView(button("📊 گزارش امروز"){reportPage()})
        set(r)
    }
    private fun customerPage(){
        val r=base("مشتری‌ها"); r.addView(button("➕ افزودن مشتری"){addCustomerDialog()})
        customers.forEach { c->
            val row=TextView(this); row.text="👤 ${c.name}${if(c.phone.isNotBlank())"  |  ${c.phone}" else ""}"; row.textSize=16f; row.setPadding(20,18,20,18); r.addView(row)
        }
        r.addView(button("بازگشت"){showHome()}); set(r)
    }
    private fun addCustomerDialog(){
        val box=LinearLayout(this); box.orientation=LinearLayout.VERTICAL
        val n=EditText(this); n.hint="نام مشتری"; val ph=EditText(this); ph.hint="تلفن (اختیاری)"
        box.addView(n); box.addView(ph)
        AlertDialog.Builder(this).setTitle("مشتری جدید").setView(box).setPositiveButton("ذخیره"){_,_-> if(n.text.toString().trim().isNotEmpty()){customers.add(Customer(nextId++,n.text.toString().trim(),ph.text.toString()));save();customerPage()}}.setNegativeButton("انصراف",null).show()
    }
    private fun newOrder(){
        if(customers.isEmpty()){ AlertDialog.Builder(this).setMessage("ابتدا حداقل یک مشتری ثبت کنید.").setPositiveButton("باشه",null).show(); return }
        val r=base("📝 سفارش جدید")
        val sp=Spinner(this); sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,customers.map{it.name}); r.addView(label("مشتری")); r.addView(sp)
        val rows=mutableMapOf<String,EditText>()
        listOf("خوردن","خورشی","ماهی").forEach { k->
            r.addView(label("تعداد بسته $k"))
            val e=EditText(this); e.inputType=2; e.hint="۰"; rows[k]=e; r.addView(e)
        }
        r.addView(button("💾 ثبت سفارش"){ val items=mutableListOf<OrderItem>(); rows.forEach{(k,e)-> val q=e.text.toString().toIntOrNull()?:0; if(q>0)items.add(OrderItem(k,q))}
            if(items.isEmpty()){Toast.makeText(this,"تعداد حداقل یک نوع بسته را وارد کنید.",Toast.LENGTH_SHORT).show()} else {
                orders.add(Order(nextId++,customers[sp.selectedItemPosition].id,SimpleDateFormat("yyyy/MM/dd",Locale.US).format(Date()),items));save();Toast.makeText(this,"سفارش ثبت شد.",Toast.LENGTH_SHORT).show();showHome()
            }})
        r.addView(button("بازگشت"){showHome()}); set(r)
    }
    private fun invoicePage(){
        val r=base("🧾 فاکتورها")
        if(orders.isEmpty()) r.addView(label("هنوز سفارشی ثبت نشده است."))
        orders.asReversed().forEach { o->
            val c=customers.find{it.id==o.customerId}; val tv=TextView(this)
            tv.text="${if(o.invoiced)"✅ فاکتور شده" else "⏳ فاکتور نشده"}\n${c?.name?:"مشتری"} — ${o.date}\n"+o.items.joinToString(" | "){"${it.kind}: ${it.qty} بسته"}
            tv.textSize=15f; tv.setPadding(18,16,18,8); r.addView(tv)
            if(!o.invoiced) r.addView(button("🧾 صدور فاکتور"){o.invoiced=true;save();invoicePage()})
        }
        r.addView(button("بازگشت"){showHome()});set(r)
    }
    private fun reportPage(){
        val r=base("📊 گزارش امروز")
        val d=SimpleDateFormat("yyyy/MM/dd",Locale.US).format(Date())
        val today=orders.filter{it.date==d}; val sums=mutableMapOf("خوردن" to 0,"خورشی" to 0,"ماهی" to 0)
        today.forEach{o->o.items.forEach{i->sums[i.kind]=(sums[i.kind]?:0)+i.qty}}
        r.addView(label("تعداد کل سفارش‌ها: ${today.size}"))
        sums.forEach{(k,v)-> val x=TextView(this);x.text="🥬 بسته $k: $v";x.textSize=18f;x.setPadding(20,15,20,15);r.addView(x)}
        r.addView(button("بازگشت"){showHome()});set(r)
    }
}
