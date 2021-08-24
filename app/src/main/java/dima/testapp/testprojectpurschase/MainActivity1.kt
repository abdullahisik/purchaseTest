package dima.testapp.testprojectpurschase

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_1)

        supportFragmentManager.beginTransaction().replace(R.id.fragment,TestFragment()).commit()
    }
}