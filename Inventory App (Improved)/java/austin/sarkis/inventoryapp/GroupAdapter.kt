package austin.sarkis.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying a list of groups in a RecyclerView.
 *
 * Uses [ListAdapter] with [DiffUtil] to efficiently handle updates.
 * Each group item shows the group name, role, and optional description.
 *
 * @param onClick Callback invoked when a group item is selected.
 */
class GroupsAdapter(
    private val onClick: (GroupSummary) -> Unit
) : ListAdapter<GroupSummary, GroupsAdapter.VH>(Diff()) {

    /**
     * Inflates the group list item view and creates a [VH] ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.groups_list_item, parent, false)
        return VH(view, onClick)
    }

    /**
     * Binds data for the group at the given position to the [VH].
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for a group item.
     * Holds references to the views for group name, role, and description.
     */
    class VH(
        itemView: View,
        private val onGroupClicked: (GroupSummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val groupNameTextView: TextView = itemView.findViewById(R.id.textGroupName)
        private val roleTextView: TextView = itemView.findViewById(R.id.textRole)
        private val groupDescriptionTextView: TextView = itemView.findViewById(R.id.textGroupDesc)

        /**
         * Populates the ViewHolder's views with the groupSummary's data.
         *
         * @param groupSummary The [GroupSummary] item to bind.
         */
        fun bind(groupSummary: GroupSummary) {
            groupNameTextView.text = groupSummary.name
            roleTextView.text = groupSummary.role.name
            groupDescriptionTextView.text = groupSummary.description.orEmpty()

            // Handle item click
            itemView.setOnClickListener { onGroupClicked(groupSummary) }
        }
    }

    /**
     * DiffUtil callback to optimize list updates.
     * Ensures changes are detected at both item identity and content levels.
     */
    class Diff : DiffUtil.ItemCallback<GroupSummary>() {
        override fun areItemsTheSame(oldItem: GroupSummary, newItem: GroupSummary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GroupSummary, newItem: GroupSummary): Boolean =
            oldItem == newItem
    }
}
