package com.mob.lee.fastair.model

import android.provider.MediaStore
import com.mob.lee.fastair.R

class PDFCategory(override val title : Int= R.string.category_pdf) :Category(){

    override fun select() = "${MediaStore.MediaColumns.DATA} LIKE ?"

    override fun value() = arrayOf("%.pdf")
}