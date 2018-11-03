package org.tasks.tasklist;

import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;

import android.graphics.Canvas;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.todoroo.astrid.activity.TaskListFragment;
import com.todoroo.astrid.adapter.TaskAdapter;
import com.todoroo.astrid.utility.Flags;

public class ItemTouchHelperCallback extends ItemTouchHelper.Callback {
  private final TaskAdapter adapter;
  private final TaskListRecyclerAdapter recyclerAdapter;
  private final TaskListFragment taskList;
  private int from = -1;
  private int to = -1;

  ItemTouchHelperCallback(
      TaskAdapter adapter, TaskListRecyclerAdapter recyclerAdapter, TaskListFragment taskList) {
    this.adapter = adapter;
    this.recyclerAdapter = recyclerAdapter;
    this.taskList = taskList;
  }

  @Override
  public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
    super.onSelectedChanged(viewHolder, actionState);
    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
      recyclerAdapter.startActionMode();
      ((ViewHolder) viewHolder).setMoving(true);
    }
  }

  @Override
  public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
    return adapter.isManuallySorted() && adapter.getNumSelected() == 0
        ? makeMovementFlags(UP | DOWN, getSwipeFlags((ViewHolder) viewHolder))
        : makeMovementFlags(0, 0);
  }

  private int getSwipeFlags(ViewHolder vh) {
    int indentFlags = 0;
    if (vh.isIndented()) {
      indentFlags |= ItemTouchHelper.LEFT;
    }
    int position = vh.getAdapterPosition();
    if (position > 0 && adapter.canIndent(position, vh.task)) {
      indentFlags |= ItemTouchHelper.RIGHT;
    }
    return indentFlags;
  }

  @Override
  public boolean onMove(
      RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
    recyclerAdapter.finishActionMode();
    int fromPosition = source.getAdapterPosition();
    int toPosition = target.getAdapterPosition();
    if (from == -1) {
      ((ViewHolder) source).setSelected(false);
      from = fromPosition;
    }
    to = toPosition;
    recyclerAdapter.notifyItemMoved(fromPosition, toPosition);
    return true;
  }

  @Override
  public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
    return .2f;
  }

  @Override
  public void onChildDraw(
      Canvas c,
      RecyclerView recyclerView,
      RecyclerView.ViewHolder viewHolder,
      float dX,
      float dY,
      int actionState,
      boolean isCurrentlyActive) {
    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
      float shiftSize = ((ViewHolder) viewHolder).getShiftSize();
      dX = Math.max(-shiftSize, Math.min(shiftSize, dX));
    }
    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
  }

  @Override
  public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
    super.clearView(recyclerView, viewHolder);
    ViewHolder vh = (ViewHolder) viewHolder;
    vh.setMoving(false);
    if (recyclerAdapter.isActionModeActive()) {
      recyclerAdapter.toggle(vh);
    } else {
      if (from >= 0 && from != to) {
        if (from < to) {
          to++;
        }
        adapter.moved(from, to);
        taskList.loadTaskListContent(false);
      }
    }
    from = -1;
    to = -1;
    Flags.checkAndClear(Flags.TLFP_NO_INTERCEPT_TOUCH);
  }

  @Override
  public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
    adapter.indented(viewHolder.getAdapterPosition(), direction == ItemTouchHelper.RIGHT ? 1 : -1);
    taskList.loadTaskListContent(false);
  }
}
