package com.example.ventiladorambiental

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingPagerAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {

    class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgOnboarding: ImageView = view.findViewById(R.id.imgOnboarding)
        val txtTitle: TextView = view.findViewById(R.id.txtTitleOnboarding)
        val txtDescription: TextView = view.findViewById(R.id.txtDescriptionOnboarding)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val page = pages[position]
        holder.txtTitle.text = page.title
        holder.txtDescription.text = page.description
        holder.imgOnboarding.setImageResource(page.imageResId)
    }

    override fun getItemCount(): Int = pages.size
}
