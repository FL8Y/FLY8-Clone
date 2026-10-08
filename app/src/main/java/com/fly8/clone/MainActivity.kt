package com.fly8.clone

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(11,13,12)
    private val panel = Color.rgb(21,25,21)
    private val panel2 = Color.rgb(28,33,28)
    private val mint = Color.rgb(139,224,181)
    private val fg = Color.rgb(241,245,241)
    private val muted = Color.rgb(169,178,171)
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var store: CloneStore
    private val clones = mutableListOf<CloneEntry>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = CloneStore(this)
        clones.addAll(store.load())
        build()
        render()
        Shizuku.addBinderReceivedListener { runOnUiThread { updateStatus() } }
        Shizuku.addBinderDeadListener { runOnUiThread { updateStatus() } }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener { updateStatus() }
        Shizuku.removeBinderDeadListener { updateStatus() }
        super.onDestroy()
    }

    private fun build() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val head = LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(20),dp(18),dp(20),dp(6))
        }
        head.addView(TextView(this).apply {
            text="FLY8"
            textSize=28f
            setTextColor(fg)
            typeface=android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0,dp(52),1f))
        head.addView(SwitchMaterial(this).apply {
            text="Shizuku"
            textSize=13f
            setTextColor(muted)
            isChecked=shizukuReady()
            setOnClickListener {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(7)
                }
                updateStatus()
            }
        })
        root.addView(head)

        status=TextView(this).apply {
            setTextColor(muted); textSize=13f
            setPadding(dp(20),0,dp(20),dp(12))
        }
        root.addView(status)

        val scroll=ScrollView(this)
        list=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),0,dp(16),dp(24))
        }
        scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

        root.addView(MaterialButton(this).apply {
            text="Add app"
            setOnClickListener { picker() }
            setTextColor(bg)
            setBackgroundColor(mint)
        },LinearLayout.LayoutParams(-1,dp(54)).apply {
            setMargins(dp(16),dp(8),dp(16),dp(16))
        })
        setContentView(root)
    }

    private fun render() {
        list.removeAllViews()
        updateStatus()
        title("Clones")
        if (clones.isEmpty()) card("Nothing here yet","Add an installed app to create an isolated Android clone profile.")
        clones.toList().forEach { cloneCard(it) }
        title("Beta backend")
        card("Keyless by design","This build has no activation screen, license server, Discord integration, or remote account gate.")
        if (Build.VERSION.SDK_INT < 35) card("Android version note","The system clone-profile backend requires Android 15 or newer and device/OEM support.")
    }

    private fun updateStatus() {
        val s = when {
            !Shizuku.pingBinder() -> "Shizuku unavailable"
            Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED -> "Shizuku permission needed"
            else -> "Shizuku connected"
        }
        status.text=s+"  •  Android "+Build.VERSION.RELEASE+"  •  "+clones.size+" clone"+if(clones.size==1)"" else "s"
    }

    private fun picker() {
        val pm=packageManager
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps=pm.queryIntentActivities(intent,PackageManager.MATCH_ALL)
            .mapNotNull {
                val a=it.activityInfo?.applicationInfo ?: return@mapNotNull null
                if(a.packageName==packageName) null
                else Triple(a.loadLabel(pm).toString(),a.packageName,a.loadIcon(pm))
            }.distinctBy{it.second}.sortedBy{it.first.lowercase(Locale.ROOT)}

        val d=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));setBackgroundColor(panel)}
        root.addView(TextView(this).apply{text="Choose an installed app";textSize=21f;setTextColor(fg)})
        val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        apps.take(150).forEach{(label,pkg,icon)->
            rows.addView(LinearLayout(this).apply{
                gravity=Gravity.CENTER_VERTICAL
                setPadding(0,dp(6),0,dp(6))
                addView(ImageView(this@MainActivity).apply{setImageDrawable(icon)},LinearLayout.LayoutParams(dp(44),dp(44)))
                addView(TextView(this@MainActivity).apply{text=label+"\n"+pkg;textSize=14f;setTextColor(fg);setPadding(dp(12),0,0,0)},LinearLayout.LayoutParams(0,dp(58),1f))
                setOnClickListener{d.dismiss();createClone(pkg,label)}
            })
        }
        root.addView(ScrollView(this).apply{addView(rows)},LinearLayout.LayoutParams(-1,0,1f))
        root.addView(MaterialButton(this).apply{text="Close";setOnClickListener{d.dismiss()}})
        d.setContentView(root)
        d.show()
        d.window?.setLayout(dp(370),dp(650))
    }

    private fun createClone(pkg:String,label:String) {
        if(!shizukuReady()){toast("Start Shizuku and grant FLY8 permission first.");return}
        Thread {
            val made=Shell.run("pm create-user --user-type android.os.usertype.profile.CLONE --profileOf 0 FLY8")
            val id=Regex("(?i)(?:Success: created user id |created user id )([0-9]+)").find(made.out+"\n"+made.err)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if(made.code!=0 || id==null){runOnUiThread{toast(made.err.ifBlank{"Clone profile creation failed."})};return@Thread}
            val installed=Shell.run("pm install-existing --user "+id+" "+pkg)
            if(installed.code!=0){
                Shell.run("pm remove-user "+id)
                runOnUiThread{toast(installed.err.ifBlank{"Could not install the app into the clone."})}
                return@Thread
            }
            clones += CloneEntry(pkg,label,id)
            store.save(clones)
            runOnUiThread{render();toast("Clone created.")}
        }.start()
    }

    private fun cloneCard(c:CloneEntry) {
        val card=MaterialCardView(this).apply{radius=dp(18).toFloat();setCardBackgroundColor(panel);strokeWidth=dp(1);strokeColor=Color.rgb(42,50,44)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14))}
        box.addView(TextView(this).apply{text=c.label;textSize=18f;setTextColor(fg);typeface=android.graphics.Typeface.DEFAULT_BOLD})
        box.addView(TextView(this).apply{text=c.packageName+"  •  user "+c.userId;textSize=12f;setTextColor(muted);setPadding(0,dp(4),0,dp(10))})
        val actions=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        actions.addView(btn("Launch"){launch(c)},LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(btn("Clear"){clear(c)},LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(btn("Remove"){remove(c)},LinearLayout.LayoutParams(0,dp(46),1f))
        box.addView(actions);card.addView(box)
        list.addView(card,LinearLayout.LayoutParams(-1,dp(138)).apply{bottomMargin=dp(10)})
    }

    private fun launch(c:CloneEntry){Thread{val r=Shell.run("am start-user "+c.userId+" >/dev/null 2>&1; monkey --user "+c.userId+" -p "+c.packageName+" -c android.intent.category.LAUNCHER 1");if(r.code!=0)runOnUiThread{toast(r.err.ifBlank{"Launch failed."})}}.start()}
    private fun clear(c:CloneEntry){Thread{val r=Shell.run("pm clear --user "+c.userId+" "+c.packageName);runOnUiThread{toast(if(r.code==0)"Clone data cleared." else r.err.ifBlank{"Clear failed."})}}.start()}
    private fun remove(c:CloneEntry){Thread{val r=Shell.run("pm remove-user "+c.userId);runOnUiThread{if(r.code==0){clones.remove(c);store.save(clones);render();toast("Clone removed.")}else toast(r.err.ifBlank{"Remove failed."})}}.start()}

    private fun shizukuReady()=try{Shizuku.pingBinder() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}catch(_:Throwable){false}
    private fun title(s:String){list.addView(TextView(this).apply{text=s;textSize=14f;setTextColor(mint);typeface=android.graphics.Typeface.DEFAULT_BOLD;setPadding(dp(4),dp(12),dp(4),dp(8))})}
    private fun card(h:String,b:String){val c=MaterialCardView(this).apply{radius=dp(18).toFloat();setCardBackgroundColor(panel2);strokeWidth=dp(1);strokeColor=Color.rgb(42,50,44)};val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(15),dp(16),dp(15))};l.addView(TextView(this).apply{text=h;textSize=16f;setTextColor(fg);typeface=android.graphics.Typeface.DEFAULT_BOLD});l.addView(TextView(this).apply{text=b;textSize=13f;setTextColor(muted);setPadding(0,dp(5),0,0)});c.addView(l);list.addView(c,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})}
    private fun btn(s:String,click:()->Unit)=MaterialButton(this).apply{text=s;textSize=12f;setOnClickListener{click()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
