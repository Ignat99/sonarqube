/*
 * SonarQube
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';
import { connect } from 'react-redux';
import ExtensionContainer from './ExtensionContainer';
import NotFound from '../NotFound';
import { getOrganizationByKey, Store } from '../../../store/rootReducer';
import { fetchOrganization } from '../../../apps/organizations/actions';
import { Organization } from '../../types';

interface StateToProps {
  organization?: Organization;
}

interface DispatchProps {
  fetchOrganization: (organizationKey: string) => void;
}

interface OwnProps {
  location: {};
  params: {
    extensionKey: string;
    organizationKey: string;
    pluginKey: string;
  };
}

type Props = OwnProps & StateToProps & DispatchProps;

class OrganizationPageExtension extends React.PureComponent<Props> {
  refreshOrganization = () =>
    this.props.organization && this.props.fetchOrganization(this.props.organization.key);

  render() {
    const { extensionKey, pluginKey } = this.props.params;
    const { organization } = this.props;

    if (!organization) {
      return null;
    }

    let pages = organization.pages || [];
    if (organization.canAdmin && organization.adminPages) {
      pages = pages.concat(organization.adminPages);
    }

    const extension = pages.find(p => p.key === `${pluginKey}/${extensionKey}`);
    return extension ? (
      <ExtensionContainer
        extension={extension}
        location={this.props.location}
        options={{ organization, refreshOrganization: this.refreshOrganization }}
      />
    ) : (
      <NotFound withContainer={false} />
    );
  }
}

const mapStateToProps = (state: Store, ownProps: OwnProps) => ({
  organization: getOrganizationByKey(state, ownProps.params.organizationKey)
});

const mapDispatchToProps = { fetchOrganization };

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(OrganizationPageExtension);
