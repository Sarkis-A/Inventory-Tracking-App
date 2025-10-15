package austin.sarkis.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying group members with optional owner actions.
 *
 * If the current user is the group owner, additional controls
 * (promote, demote, remove) are shown for other members.
 */
class MembersAdapter(
    private val isOwner: Boolean,
    private val onPromoteClicked: (MemberRow) -> Unit,
    private val onDemoteClicked: (MemberRow) -> Unit,
    private val onRemoveClicked: (MemberRow) -> Unit
) : ListAdapter<MemberRow, MembersAdapter.VH>(Diff()) {

    /**
     * Inflates a new ViewHolder for member rows.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.member_row, parent, false)
        return VH(view, isOwner, onPromoteClicked, onDemoteClicked, onRemoveClicked)
    }

    /**
     * Binds the member data to the ViewHolder at the given position.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    /**
     * ViewHolder class for individual member rows.
     *
     * Displays the member’s email and role. If the current user is the owner,
     * action buttons (promote, demote, remove) are visible for non-owner members.
     */
    class VH(
        itemView: View,
        private val isOwner: Boolean,
        private val onPromoteClicked: (MemberRow) -> Unit,
        private val onDemoteClicked: (MemberRow) -> Unit,
        private val onRemoveClicked: (MemberRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val emailTextView = itemView.findViewById<TextView>(R.id.textEmail)
        private val roleTextView = itemView.findViewById<TextView>(R.id.textRole)
        private val actionsLayout = itemView.findViewById<LinearLayout>(R.id.actionsRow)
        private val promoteButton = itemView.findViewById<Button>(R.id.btnPromote)
        private val demoteButton = itemView.findViewById<Button>(R.id.btnDemote)
        private val removeButton = itemView.findViewById<Button>(R.id.btnRemove)

        /**
         * Populates UI fields for the given member.
         *
         * @param member The group member to display.
         */
        fun bind(member: MemberRow) {
            emailTextView.text = member.email
            roleTextView.text = member.role.uppercase()

            // Show action buttons only if current user is owner and the member is not the owner.
            val showActions = isOwner && member.role != "owner"
            actionsLayout.isVisible = showActions

            // Wire up action button callbacks
            promoteButton.setOnClickListener { onPromoteClicked(member) }
            demoteButton.setOnClickListener { onDemoteClicked(member) }
            removeButton.setOnClickListener { onRemoveClicked(member) }
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     */
    class Diff : DiffUtil.ItemCallback<MemberRow>() {
        override fun areItemsTheSame(oldMember: MemberRow, newMember: MemberRow) =
            oldMember.userId == newMember.userId

        override fun areContentsTheSame(oldMember: MemberRow, newMember: MemberRow) =
            oldMember == newMember
    }
}
