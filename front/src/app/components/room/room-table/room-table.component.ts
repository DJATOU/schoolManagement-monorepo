import { Component} from '@angular/core';
import { Room } from '../../../models/room/room';
import { RoomService } from '../../../services/room.service';
import { ReusableDatatableComponent } from '../../shared/reusable-datatable/reusable-datatable.component';
import { Observable } from 'rxjs';
import { DeleteCommand } from '../../shared/reusable-datatable/DeleteCommand';

@Component({
  selector: 'app-Room-table',
  standalone: true,
  imports: [ReusableDatatableComponent],
  templateUrl: './room-table.component.html',
  styleUrl: './room-table.component.scss'
})
export class RoomTableComponent implements DeleteCommand{
  observable: Observable<any[]> = new Observable<any[]>();
  columns = [
    {
      columnDef: 'id',
      header: 'ID',
      cell: (element: Room) => `${element.id}`,
    },
    {
      columnDef: 'name',
      header: 'RESOURCE_TABLE.NAME',
      cell: (element: Room) => `${element.name}`,
    },
    {
      columnDef: 'capacity',
      header: 'RESOURCE_TABLE.CAPACITY',
      cell: (element: Room) => `${element.capacity}`,
    },
    {
      columnDef: 'description',
      header: 'COMMON.DESCRIPTION',
      cell: (element: Room) => `${element.description}`,
    },
  ];

  constructor(private roomService: RoomService) {
    this.observable = roomService.getRooms();
  }

  disableItems(id_list: Number[]): Observable<boolean> {
    return this.roomService.disableRooms(id_list);
  }
}
