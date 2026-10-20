import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import { GroupType } from '../../../models/GroupType/groupType';
import { Group } from '../../../models/group/group';
import { GroupTypeService } from '../../../services/GroupTypeService';
import { ReusableDatatableComponent } from '../../shared/reusable-datatable/reusable-datatable.component';
import { DeleteCommand } from '../../shared/reusable-datatable/DeleteCommand';

@Component({
  selector: 'app-group-type-table',
  standalone: true,
  imports: [ReusableDatatableComponent],
  templateUrl: './group-type-table.component.html',
  styleUrl: './group-type-table.component.scss'
})
export class GroupTypeTableComponent implements DeleteCommand {

  observable: Observable<any[]> = new Observable<any[]>();
  columns = [
    {
      columnDef: 'id',
      header: 'ID',
      cell: (element: GroupType) => `${element.id}`,
    },
    {
      columnDef: 'name',
      header: 'RESOURCE_TABLE.NAME',
      cell: (element: GroupType) => `${element.name}`,
    },
    {
      columnDef: 'size',
      header: 'RESOURCE_TABLE.SIZE',
      cell: (element: GroupType) => `${element.size}`,
    },
    {
      columnDef: 'description',
      header: 'COMMON.DESCRIPTION',
      cell: (element: GroupType) => `${element.description}`,
    },
  ];

  constructor(private groupTypeservice: GroupTypeService) {
    this.observable = groupTypeservice.getAllGroupTypes();
  }

  disableItems(id_list: Number[]): Observable<boolean> {
    return this.groupTypeservice.disableGroupType(id_list);
  }
}
