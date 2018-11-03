package org.tasks.tasklist;

import androidx.paging.AsyncPagedListDiffer;
import androidx.paging.PagedList;
import android.os.Bundle;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.view.ViewGroup;
import com.google.common.primitives.Longs;
import com.todoroo.astrid.activity.MainActivity;
import com.todoroo.astrid.activity.TaskListFragment;
import com.todoroo.astrid.adapter.TaskAdapter;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.utility.Flags;
import java.util.List;

public class TaskListRecyclerAdapter extends RecyclerView.Adapter<ViewHolder>
    implements ViewHolder.ViewHolderCallbacks, ListUpdateCallback {

  private static final String EXTRA_SELECTED_TASK_IDS = "extra_selected_task_ids";

  private final TaskAdapter adapter;
  private final ViewHolderFactory viewHolderFactory;
  private final TaskListFragment taskList;
  private final ActionModeProvider actionModeProvider;
  private final AsyncPagedListDiffer<Task> asyncPagedListDiffer;
  private final ItemTouchHelperCallback itemTouchHelperCallback;

  private ActionMode mode = null;
  private boolean animate;
  private RecyclerView recyclerView;

  public TaskListRecyclerAdapter(
      TaskAdapter adapter,
      ViewHolderFactory viewHolderFactory,
      TaskListFragment taskList,
      ActionModeProvider actionModeProvider) {
    this.adapter = adapter;
    this.viewHolderFactory = viewHolderFactory;
    this.taskList = taskList;
    this.actionModeProvider = actionModeProvider;
    itemTouchHelperCallback = new ItemTouchHelperCallback(adapter, this, taskList);
    asyncPagedListDiffer =
        new AsyncPagedListDiffer<>(
            this, new AsyncDifferConfig.Builder<>(new DiffCallback(adapter)).build());
  }

  public void applyToRecyclerView(RecyclerView recyclerView) {
    this.recyclerView = recyclerView;
    recyclerView.setAdapter(this);

    new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView);
  }

  public Bundle getSaveState() {
    Bundle information = new Bundle();
    List<Long> selectedTaskIds = adapter.getSelected();
    information.putLongArray(EXTRA_SELECTED_TASK_IDS, Longs.toArray(selectedTaskIds));
    return information;
  }

  public void restoreSaveState(Bundle savedState) {
    long[] longArray = savedState.getLongArray(EXTRA_SELECTED_TASK_IDS);
    if (longArray != null && longArray.length > 0) {
      mode = actionModeProvider.startActionMode(adapter, taskList, this);
      adapter.setSelected(longArray);

      updateModeTitle();
    }
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return viewHolderFactory.newViewHolder(parent, this);
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    Task task = asyncPagedListDiffer.getItem(position);
    if (task != null) {
      holder.bindView(task);
      holder.setMoving(false);
      int indent = adapter.getIndent(task);
      task.setIndent(indent);
      holder.setIndent(indent);
      holder.setSelected(adapter.isSelected(task));
    }
  }

  @Override
  public int getItemCount() {
    return asyncPagedListDiffer.getItemCount();
  }

  @Override
  public void onCompletedTask(Task task, boolean newState) {
    adapter.onCompletedTask(task, newState);
  }

  @Override
  public void onClick(ViewHolder viewHolder) {
    if (mode == null) {
      taskList.onTaskListItemClicked(viewHolder.task);
    } else {
      toggle(viewHolder);
    }
  }

  @Override
  public void onClick(Filter filter) {
    if (mode == null) {
      MainActivity activity = (MainActivity) taskList.getActivity();
      activity.onFilterItemClicked(filter);
    }
  }

  @Override
  public boolean onLongPress(ViewHolder viewHolder) {
    if (!adapter.isManuallySorted()) {
      startActionMode();
    }
    if (mode != null && !viewHolder.isMoving()) {
      toggle(viewHolder);
    }
    return true;
  }

  void startActionMode() {
    if (mode == null) {
      mode = actionModeProvider.startActionMode(adapter, taskList, this);
      updateModeTitle();
      if (adapter.isManuallySorted()) {
        Flags.set(Flags.TLFP_NO_INTERCEPT_TOUCH);
      }
    }
  }

  void toggle(ViewHolder viewHolder) {
    adapter.toggleSelection(viewHolder.task);
    notifyItemChanged(viewHolder.getAdapterPosition());
    if (adapter.getSelected().isEmpty()) {
      finishActionMode();
    } else {
      updateModeTitle();
    }
  }

  private void updateModeTitle() {
    if (mode != null) {
      int count = Math.max(1, adapter.getNumSelected());
      mode.setTitle(Integer.toString(count));
    }
  }

  public void finishActionMode() {
    if (mode != null) {
      mode.finish();
    }
  }

  @Override
  public void onInserted(int position, int count) {
    notifyItemRangeInserted(position, count);
  }

  @Override
  public void onRemoved(int position, int count) {
    notifyItemRangeRemoved(position, count);
  }

  @Override
  public void onMoved(int fromPosition, int toPosition) {
    if (animate) {
      notifyItemChanged(fromPosition);
      notifyItemMoved(fromPosition, toPosition);
      recyclerView.scrollToPosition(fromPosition);
    } else {
      notifyDataSetChanged();
    }
  }

  @Override
  public void onChanged(int position, int count, Object payload) {
    if (animate) {
      notifyItemRangeChanged(position, count, payload);
    } else {
      notifyDataSetChanged();
    }
  }

  public void onTaskSaved() {
    setAnimate(true);
    int scrollY = recyclerView.getScrollY();
    notifyDataSetChanged();
    recyclerView.setScrollY(scrollY);
  }

  public void setList(PagedList<Task> list) {
    asyncPagedListDiffer.submitList(list);
  }

  public void setAnimate(boolean animate) {
    this.animate = animate;
  }

  public AsyncPagedListDiffer<Task> getAsyncPagedListDiffer() {
    return asyncPagedListDiffer;
  }

  boolean isActionModeActive() {
    return mode != null;
  }

  void onDestroyActionMode() {
    mode = null;
  }
}
